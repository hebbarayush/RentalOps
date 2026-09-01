package com.rentalops.payment;

import com.rentalops.auth.CurrentUserService;
import com.rentalops.common.NotFoundException;
import com.rentalops.common.SpecFilters;
import com.rentalops.lease.Lease;
import com.rentalops.lease.LeaseRepository;
import com.rentalops.property.PropertyService;
import com.rentalops.tenant.Tenant;
import com.rentalops.tenant.TenantService;
import com.rentalops.user.User;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RentPaymentService {
    private final RentPaymentRepository paymentRepository;
    private final LeaseRepository leaseRepository;
    private final PropertyService propertyService;
    private final TenantService tenantService;
    private final CurrentUserService currentUserService;
    private final RentBillingService rentBillingService;

    public RentPaymentService(
            RentPaymentRepository paymentRepository,
            LeaseRepository leaseRepository,
            PropertyService propertyService,
            TenantService tenantService,
            CurrentUserService currentUserService,
            RentBillingService rentBillingService
    ) {
        this.paymentRepository = paymentRepository;
        this.leaseRepository = leaseRepository;
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.currentUserService = currentUserService;
        this.rentBillingService = rentBillingService;
    }

    /** Admin-triggered global billing run. */
    public int runBilling() {
        if (!currentUserService.isAdmin(currentUserService.requireCurrentUser())) {
            throw new AccessDeniedException("Admin role required");
        }
        return rentBillingService.generateAllNow();
    }

    @Transactional
    public RentPaymentResponse create(RentPaymentRequest request) {
        requireManager();
        Lease lease = leaseRepository.findById(request.leaseId())
                .orElseThrow(() -> new NotFoundException("Lease not found"));
        propertyService.requireManaged(lease.getProperty().getId());
        return RentPaymentResponse.from(paymentRepository.save(new RentPayment(lease, request)));
    }

    @Transactional(readOnly = true)
    public Page<RentPaymentResponse> list(RentPaymentFilter filter, Pageable pageable) {
        User current = currentUserService.requireCurrentUser();
        List<Specification<RentPayment>> specs = new ArrayList<>();

        if (!currentUserService.isAdmin(current)) {
            if (currentUserService.isManagerOrAdmin(current)) {
                specs.add((root, q, cb) -> cb.equal(root.get("property").get("manager"), current));
            } else {
                Tenant tenant = tenantService.requireCurrentTenant();
                specs.add((root, q, cb) -> cb.equal(root.get("tenant"), tenant));
            }
        }
        if (filter.status() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("paymentStatus"), filter.status()));
        }
        if (Boolean.TRUE.equals(filter.unpaidOnly())) {
            specs.add((root, q, cb) -> cb.notEqual(root.get("paymentStatus"), PaymentStatus.PAID));
        }
        if (SpecFilters.has(filter.q())) {
            String like = SpecFilters.like(filter.q());
            specs.add((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("notes")), like),
                    cb.like(cb.lower(root.get("transactionReference")), like)));
        }
        return paymentRepository.findAll(SpecFilters.combine(specs), pageable).map(RentPaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<RentPaymentResponse> listForTenant(Long tenantId, Pageable pageable) {
        tenantService.requireAccessible(tenantId);
        return paymentRepository.findByTenantId(tenantId, pageable).map(RentPaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public RentPaymentResponse get(Long id) {
        return RentPaymentResponse.from(requireReadablePayment(id));
    }

    @Transactional
    public RentPaymentResponse markPaid(Long id, MarkPaymentRequest request) {
        requireManager();
        RentPayment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        propertyService.requireManaged(payment.getProperty().getId());
        payment.markPaid(request);
        return RentPaymentResponse.from(payment);
    }

    private RentPayment requireReadablePayment(Long id) {
        User current = currentUserService.requireCurrentUser();
        RentPayment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        if (currentUserService.isManagerOrAdmin(current)) {
            propertyService.requireAccessible(payment.getProperty().getId());
            return payment;
        }
        Tenant tenant = tenantService.requireCurrentTenant();
        if (!payment.getTenant().getId().equals(tenant.getId())) {
            throw new AccessDeniedException("Payment is not accessible to the current user");
        }
        return payment;
    }

    private void requireManager() {
        if (!currentUserService.isManagerOrAdmin(currentUserService.requireCurrentUser())) {
            throw new AccessDeniedException("Property manager role required");
        }
    }
}
