package com.rentalops.payment;

import com.rentalops.common.events.RentChargeGeneratedEvent;
import com.rentalops.common.jobs.JobLockService;
import com.rentalops.lease.Lease;
import com.rentalops.lease.LeaseRepository;
import com.rentalops.lease.LeaseStatus;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns each active lease into a stream of monthly rent charges. A lease's
 * {@code nextChargeDate} pointer is walked forward, creating one {@link RentPayment}
 * per billing period, up to {@code generate-lead-days} ahead of today.
 */
@Service
public class RentBillingService {
    private static final Logger log = LoggerFactory.getLogger(RentBillingService.class);

    private final LeaseRepository leaseRepository;
    private final RentPaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JobLockService jobLockService;
    private final int leadDays;
    private final boolean autorun;
    // Self-injected proxy so generateAllNow() is invoked through Spring AOP (its @Transactional
    // must apply) even though it's called from a Runnable created inside this same bean.
    private final RentBillingService self;

    public RentBillingService(
            LeaseRepository leaseRepository,
            RentPaymentRepository paymentRepository,
            ApplicationEventPublisher eventPublisher,
            JobLockService jobLockService,
            @Value("${app.rent.generate-lead-days:7}") int leadDays,
            @Value("${app.jobs.autorun:true}") boolean autorun,
            @org.springframework.context.annotation.Lazy RentBillingService self
    ) {
        this.leaseRepository = leaseRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
        this.jobLockService = jobLockService;
        this.leadDays = leadDays;
        this.autorun = autorun;
        this.self = self;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 15 2 * * *")
    public void generateDueCharges() {
        if (autorun) {
            // Only one instance actually bills; others skip this tick.
            jobLockService.runLocked("rent-billing", Duration.ofMinutes(10), self::generateAllNow);
        }
    }

    /** Run billing across every active lease now. Returns the number of charges created. */
    @Transactional
    public int generateAllNow() {
        LocalDate cutoff = LocalDate.now().plusDays(leadDays);
        List<Lease> leases = leaseRepository
                .findByLeaseStatusAndNextChargeDateLessThanEqual(LeaseStatus.ACTIVE, cutoff);
        int created = 0;
        for (Lease lease : leases) {
            created += generate(lease, cutoff);
        }
        if (created > 0) {
            log.info("Rent billing: generated {} charge(s) across {} lease(s)", created, leases.size());
        }
        return created;
    }

    /** Generate any charges now due for a single lease (used by the manual trigger). */
    @Transactional
    public int generateForLease(Long leaseId) {
        Lease lease = leaseRepository.findById(leaseId).orElseThrow();
        return generate(lease, LocalDate.now().plusDays(leadDays));
    }

    private int generate(Lease lease, LocalDate cutoff) {
        int created = 0;
        while (lease.getNextChargeDate() != null
                && !lease.getNextChargeDate().isAfter(cutoff)
                && !lease.getNextChargeDate().isAfter(lease.getEndDate())) {
            LocalDate due = lease.getNextChargeDate();
            if (!paymentRepository.existsByLeaseIdAndDueDate(lease.getId(), due)) {
                RentPayment charge = paymentRepository.save(new RentPayment(
                        lease,
                        new RentPaymentRequest(lease.getId(), lease.getMonthlyRent(), due, "Auto-generated rent charge")));
                var tenantUser = lease.getTenant().getUser();
                eventPublisher.publishEvent(new RentChargeGeneratedEvent(
                        charge.getId(), tenantUser == null ? null : tenantUser.getId(),
                        lease.getMonthlyRent(), due, lease.getProperty().getName()));
                created++;
            }
            lease.advanceNextChargeDate();
        }
        return created;
    }
}
