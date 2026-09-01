package com.rentalops.property;

import com.rentalops.auth.CurrentUserService;
import com.rentalops.common.NotFoundException;
import com.rentalops.lease.LeaseRepository;
import com.rentalops.lease.LeaseStatus;
import com.rentalops.tenant.Tenant;
import com.rentalops.tenant.TenantRepository;
import com.rentalops.user.RoleName;
import com.rentalops.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyService {
    private final PropertyRepository propertyRepository;
    private final LeaseRepository leaseRepository;
    private final TenantRepository tenantRepository;
    private final CurrentUserService currentUserService;

    public PropertyService(
            PropertyRepository propertyRepository,
            LeaseRepository leaseRepository,
            TenantRepository tenantRepository,
            CurrentUserService currentUserService
    ) {
        this.propertyRepository = propertyRepository;
        this.leaseRepository = leaseRepository;
        this.tenantRepository = tenantRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public PropertyResponse create(PropertyRequest request) {
        User current = currentUserService.requireCurrentUser();
        requireManager(current);
        return PropertyResponse.from(propertyRepository.save(new Property(current, request)));
    }

    @Transactional(readOnly = true)
    public Page<PropertyResponse> list(PropertyFilter filter, Pageable pageable) {
        User current = currentUserService.requireCurrentUser();

        if (!currentUserService.isManagerOrAdmin(current)) {
            // Tenant: the properties they hold a lease on (no filtering).
            Tenant tenant = tenantRepository.findByUser(current).orElse(null);
            List<PropertyResponse> leased = tenant == null
                    ? List.of()
                    : propertyRepository.findLeasedByTenantId(tenant.getId()).stream()
                            .map(PropertyResponse::from)
                            .toList();
            return new PageImpl<>(leased, pageable, leased.size());
        }

        List<Specification<Property>> specs = new ArrayList<>();
        if (!currentUserService.isAdmin(current)) {
            specs.add((root, q, cb) -> cb.equal(root.get("manager"), current));
        }
        if (filter.city() != null && !filter.city().isBlank()) {
            specs.add((root, q, cb) -> cb.equal(cb.lower(root.get("city")), filter.city().toLowerCase()));
        }
        if (filter.status() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.type() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("propertyType"), filter.type()));
        }
        if (filter.q() != null && !filter.q().isBlank()) {
            String like = "%" + filter.q().toLowerCase() + "%";
            specs.add((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("addressLine1")), like),
                    cb.like(cb.lower(root.get("city")), like)));
        }
        Specification<Property> combined = specs.stream().reduce(Specification::and).orElse(null);
        return propertyRepository.findAll(combined, pageable).map(PropertyResponse::from);
    }

    @Transactional(readOnly = true)
    public PropertyResponse get(Long id) {
        return PropertyResponse.from(requireAccessible(id));
    }

    @Transactional
    public PropertyResponse update(Long id, PropertyRequest request) {
        Property property = requireManaged(id);
        property.update(request);
        return PropertyResponse.from(property);
    }

    @Transactional
    public void deactivate(Long id) {
        Property property = requireManaged(id);
        if (leaseRepository.existsByPropertyAndLeaseStatus(property, LeaseStatus.ACTIVE)) {
            throw new IllegalArgumentException("Cannot deactivate a property that still has active leases");
        }
        property.deactivate();
    }

    /** Manager/admin who owns it, or a tenant with a lease on it. Read access. */
    public Property requireAccessible(Long id) {
        User current = currentUserService.requireCurrentUser();
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Property not found"));
        if (currentUserService.isAdmin(current)) {
            return property;
        }
        if (currentUserService.isManagerOrAdmin(current)
                && Objects.equals(property.getManager().getId(), current.getId())) {
            return property;
        }
        Tenant tenant = tenantRepository.findByUser(current).orElse(null);
        if (tenant != null && leaseRepository.existsByPropertyAndTenant(property, tenant)) {
            return property;
        }
        throw new AccessDeniedException("Property is not accessible to the current user");
    }

    /**
     * Row-locks the property for the rest of the current transaction. Call this before
     * mutating {@code occupiedUnits} so two concurrent lease activations/terminations for the
     * same property serialize instead of racing.
     */
    @Transactional
    public void lockForOccupancyChange(Long id) {
        propertyRepository.lockById(id).orElseThrow(() -> new NotFoundException("Property not found"));
    }

    /** Manager/admin who owns it. Write access. */
    public Property requireManaged(Long id) {
        User current = currentUserService.requireCurrentUser();
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Property not found"));
        if (currentUserService.isAdmin(current)) {
            return property;
        }
        if (currentUserService.isManagerOrAdmin(current)
                && Objects.equals(property.getManager().getId(), current.getId())) {
            return property;
        }
        throw new AccessDeniedException("Property does not belong to the current user");
    }

    private void requireManager(User user) {
        if (!currentUserService.isManagerOrAdmin(user)) {
            throw new AccessDeniedException("Required role: " + RoleName.PROPERTY_MANAGER);
        }
    }
}
