package com.rentalops.maintenance;

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
@RequestMapping("/api/maintenance-requests")
public class MaintenanceController {
    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @PostMapping
    public MaintenanceResponse create(@Valid @RequestBody MaintenanceCreateRequest request) {
        return maintenanceService.create(request);
    }

    @GetMapping
    public Page<MaintenanceResponse> list(
            @RequestParam(required = false) MaintenanceStatus status,
            @RequestParam(required = false) MaintenancePriority priority,
            @RequestParam(required = false) String q,
            Pageable pageable
    ) {
        return maintenanceService.list(new MaintenanceFilter(status, priority, q), pageable);
    }

    @GetMapping("/{id}")
    public MaintenanceResponse get(@PathVariable Long id) {
        return maintenanceService.get(id);
    }

    @PutMapping("/{id}")
    public MaintenanceResponse update(@PathVariable Long id, @Valid @RequestBody MaintenanceUpdateRequest request) {
        return maintenanceService.update(id, request);
    }

    @PostMapping("/{id}/retriage")
    public MaintenanceResponse retriage(@PathVariable Long id) {
        return maintenanceService.retriage(id);
    }

    @PostMapping("/{id}/accept-suggestion")
    public MaintenanceResponse acceptSuggestion(@PathVariable Long id) {
        return maintenanceService.acceptSuggestion(id);
    }
}

