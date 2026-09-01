package com.rentalops.tenant;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {
    private final TenantService tenantService;
    private final TenantReliabilityService reliabilityService;

    public TenantController(TenantService tenantService, TenantReliabilityService reliabilityService) {
        this.tenantService = tenantService;
        this.reliabilityService = reliabilityService;
    }

    @PostMapping
    public TenantResponse create(@Valid @RequestBody TenantRequest request) {
        return tenantService.create(request);
    }

    @GetMapping
    public Page<TenantResponse> list(
            @RequestParam(required = false) TenantStatus status,
            @RequestParam(required = false) String q,
            Pageable pageable
    ) {
        return tenantService.list(new TenantFilter(status, q), pageable);
    }

    @GetMapping("/me")
    public TenantResponse me() {
        return tenantService.currentTenantProfile();
    }

    @GetMapping("/{id}")
    public TenantResponse get(@PathVariable Long id) {
        return tenantService.get(id);
    }

    @GetMapping("/{id}/reliability")
    public ReliabilityResponse reliability(@PathVariable Long id) {
        return reliabilityService.forTenant(id);
    }

    @PutMapping("/{id}")
    public TenantResponse update(@PathVariable Long id, @Valid @RequestBody TenantRequest request) {
        return tenantService.update(id, request);
    }
}

