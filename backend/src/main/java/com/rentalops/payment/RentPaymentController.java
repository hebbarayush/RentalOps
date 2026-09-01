package com.rentalops.payment;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rent-payments")
public class RentPaymentController {
    private final RentPaymentService rentPaymentService;

    public RentPaymentController(RentPaymentService rentPaymentService) {
        this.rentPaymentService = rentPaymentService;
    }

    @PostMapping
    public RentPaymentResponse create(@Valid @RequestBody RentPaymentRequest request) {
        return rentPaymentService.create(request);
    }

    @GetMapping
    public Page<RentPaymentResponse> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Boolean unpaidOnly,
            @RequestParam(required = false) String q,
            Pageable pageable
    ) {
        return rentPaymentService.list(new RentPaymentFilter(status, unpaidOnly, q), pageable);
    }

    @PostMapping("/run-billing")
    public Map<String, Integer> runBilling() {
        return Map.of("created", rentPaymentService.runBilling());
    }

    @GetMapping("/tenant/{tenantId}")
    public Page<RentPaymentResponse> listForTenant(@PathVariable Long tenantId, Pageable pageable) {
        return rentPaymentService.listForTenant(tenantId, pageable);
    }

    @GetMapping("/{id}")
    public RentPaymentResponse get(@PathVariable Long id) {
        return rentPaymentService.get(id);
    }

    @PostMapping("/{id}/mark-paid")
    public RentPaymentResponse markPaid(@PathVariable Long id, @Valid @RequestBody MarkPaymentRequest request) {
        return rentPaymentService.markPaid(id, request);
    }
}
