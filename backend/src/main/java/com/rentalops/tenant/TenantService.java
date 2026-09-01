package com.rentalops.tenant;

import com.rentalops.auth.CurrentUserService;
import com.rentalops.common.NotFoundException;
import com.rentalops.common.SpecFilters;
import com.rentalops.user.RoleName;
import com.rentalops.user.User;
import com.rentalops.user.UserRepository;
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
public class TenantService {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public TenantService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public TenantResponse create(TenantRequest request) {
        User current = currentUserService.requireCurrentUser();
        requireManager(current);
        Tenant tenant = new Tenant(current, request);
        autoLinkUser(tenant);
        return TenantResponse.from(tenantRepository.save(tenant));
    }

    @Transactional(readOnly = true)
    public Page<TenantResponse> list(TenantFilter filter, Pageable pageable) {
        User current = currentUserService.requireCurrentUser();
        if (!currentUserService.isManagerOrAdmin(current)) {
            // A tenant only ever sees their own profile.
            List<TenantResponse> self = tenantRepository.findByUser(current)
                    .map(TenantResponse::from)
                    .map(List::of)
                    .orElseGet(List::of);
            return new PageImpl<>(self, pageable, self.size());
        }

        List<Specification<Tenant>> specs = new ArrayList<>();
        if (!currentUserService.isAdmin(current)) {
            specs.add((root, q, cb) -> cb.equal(root.get("manager"), current));
        }
        if (filter.status() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (SpecFilters.has(filter.q())) {
            String like = SpecFilters.like(filter.q());
            specs.add((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), like),
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("phone")), like)));
        }
        return tenantRepository.findAll(SpecFilters.combine(specs), pageable).map(TenantResponse::from);
    }

    @Transactional(readOnly = true)
    public TenantResponse get(Long id) {
        return TenantResponse.from(requireAccessible(id));
    }

    @Transactional(readOnly = true)
    public TenantResponse currentTenantProfile() {
        return TenantResponse.from(requireCurrentTenant());
    }

    @Transactional
    public TenantResponse update(Long id, TenantRequest request) {
        Tenant tenant = requireAccessible(id);
        requireManager(currentUserService.requireCurrentUser());
        tenant.update(request);
        if (tenant.getUser() == null) {
            autoLinkUser(tenant);
        }
        return TenantResponse.from(tenant);
    }

    /** Manager/admin: any tenant they own. Tenant: only their own linked record. */
    public Tenant requireAccessible(Long id) {
        User current = currentUserService.requireCurrentUser();
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tenant not found"));
        if (currentUserService.isAdmin(current)) {
            return tenant;
        }
        if (currentUserService.isManagerOrAdmin(current)
                && Objects.equals(tenant.getManager().getId(), current.getId())) {
            return tenant;
        }
        if (tenant.getUser() != null && Objects.equals(tenant.getUser().getId(), current.getId())) {
            return tenant;
        }
        throw new AccessDeniedException("Tenant is not accessible to the current user");
    }

    public Tenant requireCurrentTenant() {
        User current = currentUserService.requireCurrentUser();
        return tenantRepository.findByUser(current)
                .orElseThrow(() -> new NotFoundException(
                        "No tenant profile is linked to this account yet. Ask your property manager to add you."));
    }

    private void autoLinkUser(Tenant tenant) {
        userRepository.findByEmail(tenant.getEmail())
                .filter(u -> currentUserService.hasRole(u, RoleName.TENANT))
                .ifPresent(tenant::linkUser);
    }

    private void requireManager(User user) {
        if (!currentUserService.isManagerOrAdmin(user)) {
            throw new AccessDeniedException("Property manager role required");
        }
    }
}
