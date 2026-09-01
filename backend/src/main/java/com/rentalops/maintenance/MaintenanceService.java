package com.rentalops.maintenance;

import com.rentalops.auth.CurrentUserService;
import com.rentalops.common.NotFoundException;
import com.rentalops.common.SpecFilters;
import com.rentalops.common.events.MaintenanceCreatedEvent;
import com.rentalops.common.events.MaintenanceUpdatedEvent;
import com.rentalops.lease.LeaseRepository;
import com.rentalops.property.Property;
import com.rentalops.property.PropertyRepository;
import com.rentalops.property.PropertyService;
import com.rentalops.tenant.Tenant;
import com.rentalops.tenant.TenantService;
import com.rentalops.user.User;
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
public class MaintenanceService {
    private final MaintenanceRepository maintenanceRepository;
    private final PropertyRepository propertyRepository;
    private final LeaseRepository leaseRepository;
    private final TenantService tenantService;
    private final PropertyService propertyService;
    private final CurrentUserService currentUserService;
    private final MaintenanceTriageService triageService;
    private final ApplicationEventPublisher eventPublisher;

    public MaintenanceService(
            MaintenanceRepository maintenanceRepository,
            PropertyRepository propertyRepository,
            LeaseRepository leaseRepository,
            TenantService tenantService,
            PropertyService propertyService,
            CurrentUserService currentUserService,
            MaintenanceTriageService triageService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.maintenanceRepository = maintenanceRepository;
        this.propertyRepository = propertyRepository;
        this.leaseRepository = leaseRepository;
        this.tenantService = tenantService;
        this.propertyService = propertyService;
        this.currentUserService = currentUserService;
        this.triageService = triageService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public MaintenanceResponse create(MaintenanceCreateRequest request) {
        User current = currentUserService.requireCurrentUser();
        Tenant tenant;
        Property property;
        if (currentUserService.isManagerOrAdmin(current)) {
            tenant = tenantService.requireAccessible(request.tenantId());
            property = propertyService.requireAccessible(request.propertyId());
        } else {
            // Tenant self-service: force their own identity, validate the property is one they lease.
            tenant = tenantService.requireCurrentTenant();
            property = propertyRepository.findById(request.propertyId())
                    .orElseThrow(() -> new NotFoundException("Property not found"));
            if (!leaseRepository.existsByPropertyAndTenant(property, tenant)) {
                throw new AccessDeniedException("You can only raise requests for a property you lease");
            }
        }

        MaintenanceRequest saved = maintenanceRepository.save(new MaintenanceRequest(tenant, property, request));

        MaintenanceTriageService.Result t = triageService.triage(request.title(), request.description());
        saved.applyTriage(t.source(), t.category(), t.priority(), t.summary(), t.costBand(), t.draftReply());

        eventPublisher.publishEvent(new MaintenanceCreatedEvent(
                saved.getId(), property.getManager().getId(), request.title(), property.getName(),
                t.category(), t.priority().name()));

        return MaintenanceResponse.from(saved);
    }

    @Transactional
    public MaintenanceResponse retriage(Long id) {
        MaintenanceRequest req = maintenanceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Maintenance request not found"));
        propertyService.requireManaged(req.getProperty().getId());
        MaintenanceTriageService.Result t = triageService.triage(req.getTitle(), req.getDescription());
        req.applyTriage(t.source(), t.category(), t.priority(), t.summary(), t.costBand(), t.draftReply());
        return MaintenanceResponse.from(req);
    }

    @Transactional
    public MaintenanceResponse acceptSuggestion(Long id) {
        MaintenanceRequest req = maintenanceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Maintenance request not found"));
        propertyService.requireManaged(req.getProperty().getId());
        req.acceptSuggestedPriority();
        return MaintenanceResponse.from(req);
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceResponse> list(MaintenanceFilter filter, Pageable pageable) {
        User current = currentUserService.requireCurrentUser();
        List<Specification<MaintenanceRequest>> specs = new ArrayList<>();

        if (!currentUserService.isAdmin(current)) {
            if (currentUserService.isManagerOrAdmin(current)) {
                specs.add((root, q, cb) -> cb.equal(root.get("property").get("manager"), current));
            } else {
                Tenant tenant = tenantService.requireCurrentTenant();
                specs.add((root, q, cb) -> cb.equal(root.get("tenant"), tenant));
            }
        }
        if (filter.status() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.priority() != null) {
            specs.add((root, q, cb) -> cb.equal(root.get("priority"), filter.priority()));
        }
        if (SpecFilters.has(filter.q())) {
            String like = SpecFilters.like(filter.q());
            specs.add((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("description")), like)));
        }
        return maintenanceRepository.findAll(SpecFilters.combine(specs), pageable).map(MaintenanceResponse::from);
    }

    @Transactional(readOnly = true)
    public MaintenanceResponse get(Long id) {
        return MaintenanceResponse.from(requireReadable(id));
    }

    @Transactional
    public MaintenanceResponse update(Long id, MaintenanceUpdateRequest request) {
        MaintenanceRequest maintenanceRequest = maintenanceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Maintenance request not found"));
        propertyService.requireAccessible(maintenanceRequest.getProperty().getId());
        if (!currentUserService.isManagerOrAdmin(currentUserService.requireCurrentUser())) {
            throw new AccessDeniedException("Only a property manager can update a maintenance request");
        }
        maintenanceRequest.updateStatus(request);
        User tenantUser = maintenanceRequest.getTenant().getUser();
        eventPublisher.publishEvent(new MaintenanceUpdatedEvent(
                maintenanceRequest.getId(), tenantUser == null ? null : tenantUser.getId(),
                maintenanceRequest.getTitle(), request.status().name()));
        return MaintenanceResponse.from(maintenanceRequest);
    }

    private MaintenanceRequest requireReadable(Long id) {
        User current = currentUserService.requireCurrentUser();
        MaintenanceRequest maintenanceRequest = maintenanceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Maintenance request not found"));
        if (currentUserService.isManagerOrAdmin(current)) {
            propertyService.requireAccessible(maintenanceRequest.getProperty().getId());
            return maintenanceRequest;
        }
        Tenant tenant = tenantService.requireCurrentTenant();
        if (!Objects.equals(maintenanceRequest.getTenant().getId(), tenant.getId())) {
            throw new AccessDeniedException("Request is not accessible to the current user");
        }
        return maintenanceRequest;
    }
}
