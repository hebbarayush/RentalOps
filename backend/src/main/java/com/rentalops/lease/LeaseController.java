package com.rentalops.lease;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/leases")
public class LeaseController {
    private final LeaseService leaseService;

    public LeaseController(LeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @PostMapping
    public LeaseResponse create(@Valid @RequestBody LeaseRequest request) {
        return leaseService.create(request);
    }

    @GetMapping
    public Page<LeaseResponse> list(
            @RequestParam(required = false) LeaseStatus status,
            @RequestParam(required = false) String q,
            Pageable pageable
    ) {
        return leaseService.list(new LeaseFilter(status, q), pageable);
    }

    @GetMapping("/expiring-soon")
    public List<LeaseResponse> expiringSoon() {
        return leaseService.expiringSoon();
    }

    @GetMapping("/{id}")
    public LeaseResponse get(@PathVariable Long id) {
        return leaseService.get(id);
    }

    @PutMapping("/{id}")
    public LeaseResponse update(@PathVariable Long id, @Valid @RequestBody LeaseRequest request) {
        return leaseService.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public LeaseResponse activate(@PathVariable Long id) {
        return leaseService.activate(id);
    }

    @PostMapping("/{id}/terminate")
    public LeaseResponse terminate(@PathVariable Long id) {
        return leaseService.terminate(id);
    }

    @PostMapping("/{id}/renew")
    public LeaseResponse renew(@PathVariable Long id, @Valid @RequestBody LeaseRenewalRequest request) {
        return leaseService.renew(id, request);
    }

    @PostMapping("/{id}/generate-charges")
    public Map<String, Integer> generateCharges(@PathVariable Long id) {
        return Map.of("created", leaseService.generateCharges(id));
    }
}
