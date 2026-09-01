package com.rentalops.lease;

import com.rentalops.auth.CurrentUserService;
import com.rentalops.common.NotFoundException;
import com.rentalops.common.events.LeaseActivatedEvent;
import com.rentalops.payment.RentBillingService;
import com.rentalops.property.Property;
import com.rentalops.property.PropertyService;
import com.rentalops.tenant.Tenant;
import com.rentalops.tenant.TenantService;
import com.rentalops.user.User;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaseService {
    private final LeaseRepository leaseRepository;
    private final PropertyService propertyService;
    private final TenantService tenantService;
    private final CurrentUserService currentUserService;
    private final RentBillingService rentBillingService;
    private final ApplicationEventPublisher eventPublisher;

    public LeaseService(
            LeaseRepository leaseRepository,
            PropertyService propertyService,
            TenantService tenantService,
            CurrentUserService currentUserService,
            RentBillingService rentBillingService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.leaseRepository = leaseRepository;
        this.propertyService = propertyService;
        this.tenantService = tenantService;
        this.currentUserService = currentUserService;
        this.rentBillingService = rentBillingService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public LeaseResponse create(LeaseRequest request) {
        Property property = propertyService.requireAccessible(request.propertyId());
        Tenant tenant = tenantService.requireAccessible(request.tenantId());
        requireManager();
        if (leaseRepository.existsByPropertyAndUnitNumberAndLeaseStatus(property, request.unitNumber(), LeaseStatus.ACTIVE)) {
            throw new IllegalArgumentException("Unit already has an active lease");
        }
        return LeaseResponse.from(leaseRepository.save(new Lease(property, tenant, request)));
    }

    @Transactional(readOnly = true)
    public Page<LeaseResponse> list(LeaseFilter filter, Pageable pageable) {
        User current = currentUserService.requireCurrentUser();
        List<Specification<Lease>> specs = new ArrayList<>();

        if (!currentUserService.isAdmin(current)) {
            if (currentUserService.isManagerOrAdmin(current)) {
                specs.add((root, q, cb) -> cb.equal(root.get("property").get("manager"), current));
            } else {
                Tenant tenant = tenantService.requireCurrentTenant();
                specs.add((root, q, cb) -> cb.equal(root.get("tenant"), tenant));
            }
        }
        if (filter.status() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("leaseStatus"), filter.status()));
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            String like = "%" + filter.q().toLowerCase() + "%";
            specs.add((root, q, cb) -> cb.like(cb.lower(root.get("unitNumber")), like));
        }

        Specification<Lease> combined = specs.stream().reduce(Specification::and).orElse(null);
        return leaseRepository.findAll(combined, pageable).map(LeaseResponse::from);
    }

    @Transactional(readOnly = true)
    public LeaseResponse get(Long id) {
        return LeaseResponse.from(requireReadableLease(id));
    }

    @Transactional
    public LeaseResponse update(Long id, LeaseRequest request) {
        Lease lease = requireManagedLease(id);
        lease.update(request);
        return LeaseResponse.from(lease);
    }

    @Transactional
    public LeaseResponse activate(Long id) {
        Lease lease = requireManagedLease(id);
        if (lease.isActive()) {
            return LeaseResponse.from(lease);
        }
        // Lock the property row so two concurrent activations for the same unit can't both
        // pass the "no active lease yet" check before either one commits (10.1 / 10.2).
        propertyService.lockForOccupancyChange(lease.getProperty().getId());
        if (leaseRepository.existsByPropertyAndUnitNumberAndLeaseStatus(lease.getProperty(), lease.getUnitNumber(), LeaseStatus.ACTIVE)) {
            throw new IllegalArgumentException("Unit already has an active lease");
        }
        lease.getProperty().occupyUnit();
        lease.activate();
        User tenantUser = lease.getTenant().getUser();
        eventPublisher.publishEvent(new LeaseActivatedEvent(
                lease.getId(), tenantUser == null ? null : tenantUser.getId(),
                lease.getUnitNumber(), lease.getProperty().getName(),
                lease.getStartDate(), lease.getEndDate()));
        return LeaseResponse.from(lease);
    }

    @Transactional
    public LeaseResponse terminate(Long id) {
        Lease lease = requireManagedLease(id);
        boolean wasActive = lease.isActive();
        if (wasActive) {
            propertyService.lockForOccupancyChange(lease.getProperty().getId());
        }
        lease.terminate();
        if (wasActive) {
            lease.getProperty().releaseUnit();
        }
        return LeaseResponse.from(lease);
    }

    /** Generate any rent charges now due for this lease (manual trigger for a manager). */
    @Transactional
    public int generateCharges(Long id) {
        requireManagedLease(id);
        return rentBillingService.generateForLease(id);
    }

    /**
     * Create a follow-on DRAFT lease for the same unit/tenant, starting the day after the
     * current lease ends (or an explicit start date), carrying forward the deposit.
     */
    @Transactional
    public LeaseResponse renew(Long id, LeaseRenewalRequest request) {
        Lease source = requireManagedLease(id);
        LocalDate start = request.startDate() != null ? request.startDate() : source.getEndDate().plusDays(1);
        LeaseRequest newTerms = new LeaseRequest(
                source.getProperty().getId(),
                source.getTenant().getId(),
                source.getUnitNumber(),
                start,
                request.endDate(),
                request.monthlyRent() != null ? request.monthlyRent() : source.getMonthlyRent(),
                request.securityDeposit() != null ? request.securityDeposit() : source.getSecurityDeposit(),
                source.getAgreementFileUrl());
        Lease renewal = new Lease(source.getProperty(), source.getTenant(), newTerms);
        renewal.markRenewedFrom(source.getId());
        return LeaseResponse.from(leaseRepository.save(renewal));
    }

    @Transactional(readOnly = true)
    public List<LeaseResponse> expiringSoon() {
        return expiringThisMonth().stream().map(LeaseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<Lease> expiringThisMonth() {
        User current = currentUserService.requireCurrentUser();
        YearMonth month = YearMonth.now();
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        return currentUserService.isAdmin(current)
                ? leaseRepository.findByLeaseStatusAndEndDateBetween(LeaseStatus.ACTIVE, from, to)
                : leaseRepository.findByPropertyManagerAndLeaseStatusAndEndDateBetween(current, LeaseStatus.ACTIVE, from, to);
    }

    /** Manager/admin who owns the property behind the lease. */
    private Lease requireManagedLease(Long id) {
        Lease lease = leaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lease not found"));
        propertyService.requireAccessible(lease.getProperty().getId());
        requireManager();
        return lease;
    }

    /** Manager/admin who owns it, or the tenant on the lease. */
    private Lease requireReadableLease(Long id) {
        User current = currentUserService.requireCurrentUser();
        Lease lease = leaseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Lease not found"));
        if (currentUserService.isManagerOrAdmin(current)) {
            propertyService.requireAccessible(lease.getProperty().getId());
            return lease;
        }
        Tenant tenant = tenantService.requireCurrentTenant();
        if (!Objects.equals(lease.getTenant().getId(), tenant.getId())) {
            throw new AccessDeniedException("Lease is not accessible to the current user");
        }
        return lease;
    }

    private void requireManager() {
        if (!currentUserService.isManagerOrAdmin(currentUserService.requireCurrentUser())) {
            throw new AccessDeniedException("Property manager role required");
        }
    }
}
