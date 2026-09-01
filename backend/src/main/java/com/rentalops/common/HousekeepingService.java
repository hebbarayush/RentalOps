package com.rentalops.common;

import com.rentalops.common.events.LeaseExpiredEvent;
import com.rentalops.common.events.LeaseExpiringEvent;
import com.rentalops.common.events.RentOverdueEvent;
import com.rentalops.common.jobs.JobLockService;
import com.rentalops.lease.Lease;
import com.rentalops.lease.LeaseRepository;
import com.rentalops.lease.LeaseStatus;
import com.rentalops.payment.PaymentStatus;
import com.rentalops.payment.RentPayment;
import com.rentalops.payment.RentPaymentRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodic maintenance of time-based state:
 * <ul>
 *   <li>PENDING rent charges past their due date become OVERDUE (and notify tenant + manager).</li>
 *   <li>ACTIVE leases past their end date become EXPIRED and free their unit.</li>
 *   <li>Managers are reminded of leases expiring in 30 / 14 / 7 / 1 days.</li>
 * </ul>
 * Runs nightly at 02:00 and once shortly after startup.
 */
@Component
public class HousekeepingService {
    private static final Logger log = LoggerFactory.getLogger(HousekeepingService.class);
    private static final Set<Long> EXPIRY_REMINDER_DAYS = Set.of(30L, 14L, 7L, 1L);

    private final RentPaymentRepository rentPaymentRepository;
    private final LeaseRepository leaseRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JobLockService jobLockService;
    private final boolean autorun;
    // Self-injected proxy so runSweep() is invoked through Spring AOP (its @Transactional must
    // apply) even though it's called from a Runnable created inside this same bean.
    private final HousekeepingService self;

    public HousekeepingService(
            RentPaymentRepository rentPaymentRepository,
            LeaseRepository leaseRepository,
            ApplicationEventPublisher eventPublisher,
            JobLockService jobLockService,
            @org.springframework.beans.factory.annotation.Value("${app.jobs.autorun:true}") boolean autorun,
            @org.springframework.context.annotation.Lazy HousekeepingService self
    ) {
        this.rentPaymentRepository = rentPaymentRepository;
        this.leaseRepository = leaseRepository;
        this.eventPublisher = eventPublisher;
        this.jobLockService = jobLockService;
        this.autorun = autorun;
        this.self = self;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 2 * * *")
    public void sweep() {
        if (!autorun) {
            return;
        }
        // Only one instance actually runs the sweep; others skip this tick.
        jobLockService.runLocked("housekeeping-sweep", Duration.ofMinutes(10), self::runSweep);
    }

    /** The actual work, callable directly from tests. */
    @Transactional
    public void runSweep() {
        LocalDate today = LocalDate.now();

        List<RentPayment> overdue = rentPaymentRepository
                .findByPaymentStatusAndDueDateBefore(PaymentStatus.PENDING, today);
        overdue.forEach(p -> {
            p.markOverdue();
            eventPublisher.publishEvent(new RentOverdueEvent(
                    p.getId(),
                    p.getTenant().getUser() == null ? null : p.getTenant().getUser().getId(),
                    p.getProperty().getManager().getId(),
                    p.getAmountDue(), p.getDueDate(), p.getProperty().getName(), p.getTenant().getFullName()));
        });

        List<Lease> expired = leaseRepository
                .findByLeaseStatusAndEndDateBefore(LeaseStatus.ACTIVE, today);
        expired.forEach(lease -> {
            lease.expire();
            lease.getProperty().releaseUnit();
            eventPublisher.publishEvent(new LeaseExpiredEvent(
                    lease.getId(), lease.getProperty().getManager().getId(),
                    lease.getUnitNumber(), lease.getProperty().getName(), lease.getTenant().getFullName()));
        });

        int reminders = 0;
        for (Lease lease : leaseRepository.findByLeaseStatusAndEndDateBetween(
                LeaseStatus.ACTIVE, today, today.plusDays(30))) {
            long daysLeft = ChronoUnit.DAYS.between(today, lease.getEndDate());
            if (EXPIRY_REMINDER_DAYS.contains(daysLeft)) {
                eventPublisher.publishEvent(new LeaseExpiringEvent(
                        lease.getId(), lease.getProperty().getManager().getId(),
                        lease.getUnitNumber(), lease.getProperty().getName(), lease.getEndDate(), daysLeft));
                reminders++;
            }
        }

        if (!overdue.isEmpty() || !expired.isEmpty() || reminders > 0) {
            log.info("Housekeeping: {} payment(s) OVERDUE, {} lease(s) EXPIRED, {} expiry reminder(s)",
                    overdue.size(), expired.size(), reminders);
        }
    }
}
