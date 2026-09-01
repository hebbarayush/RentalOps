package com.rentalops.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.common.jobs.JobLockService;
import com.rentalops.notification.NotificationOutboxPayload;
import com.rentalops.notification.NotificationService;
import com.rentalops.user.User;
import com.rentalops.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the {@code outbox_events} table: turns PENDING rows into real {@code Notification}s,
 * off the request path, with per-row retries.
 *
 * <ul>
 *   <li>Polls every few seconds (and once on startup), guarded by {@link JobLockService} so only
 *       one instance drains at a time.</li>
 *   <li>Each row is processed in its <em>own</em> transaction ({@link #processOne}) — one bad
 *       payload can't roll back or block the rest of the batch.</li>
 *   <li>A failing row is retried up to {@link OutboxEvent#MAX_RETRIES} times, then parked as
 *       FAILED for a human to look at (or {@link #replayFailed()}).</li>
 * </ul>
 *
 * {@link #processPendingNow()} is public and callable directly (tests, a manual "flush now"
 * trigger) — mirroring how {@code RentBillingService.generateForLease} / {@code Housekeeping
 * Service.runSweep} are exercised.
 */
@Service
public class OutboxProcessor {
    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final JobLockService jobLockService;
    private final boolean autorun;
    private final OutboxProcessor self;

    public OutboxProcessor(
            OutboxEventRepository repository,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            NotificationService notificationService,
            JobLockService jobLockService,
            @Value("${app.jobs.autorun:true}") boolean autorun,
            @org.springframework.context.annotation.Lazy OutboxProcessor self
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.jobLockService = jobLockService;
        this.autorun = autorun;
        this.self = self;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:10000}")
    public void poll() {
        if (!autorun) {
            return;
        }
        jobLockService.runLocked("outbox-processor", Duration.ofMinutes(2), self::processPendingNow);
    }

    /** Nightly: drop delivered rows older than a week so the table stays small. */
    @Scheduled(cron = "0 30 2 * * *")
    @Transactional
    public void purgeProcessed() {
        if (!autorun) {
            return;
        }
        int removed = repository.deleteProcessedBefore(Instant.now().minus(Duration.ofDays(7)));
        if (removed > 0) {
            log.info("Outbox: purged {} processed row(s)", removed);
        }
    }

    /** Process the current batch of PENDING rows. Returns how many were delivered successfully. */
    public int processPendingNow() {
        List<OutboxEvent> batch = repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        int delivered = 0;
        for (OutboxEvent event : batch) {
            if (self.processOne(event.getId())) {
                delivered++;
            }
        }
        if (delivered > 0 || batch.size() != delivered) {
            log.debug("Outbox: {} delivered, {} failed this pass", delivered, batch.size() - delivered);
        }
        return delivered;
    }

    /** Move parked (FAILED) rows back to PENDING with a fresh retry budget. Returns how many. */
    @Transactional
    public int replayFailed() {
        List<OutboxEvent> failed = repository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.FAILED);
        failed.forEach(OutboxEvent::requeue);
        return failed.size();
    }

    /**
     * One row, one transaction. Failures are swallowed here (recorded on the row, not
     * rethrown) so the loop in {@link #processPendingNow()} keeps going and the row's own
     * {@code recordFailure} update still commits.
     */
    @Transactional
    public boolean processOne(UUID id) {
        OutboxEvent event = repository.findById(id).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.PENDING) {
            return false;
        }
        try {
            NotificationOutboxPayload payload =
                    objectMapper.readValue(event.getPayload(), NotificationOutboxPayload.class);
            User recipient = payload.recipientUserId() == null
                    ? null
                    : userRepository.findById(payload.recipientUserId()).orElse(null);
            notificationService.notify(recipient, payload.type(), payload.title(),
                    payload.message(), payload.linkType(), payload.linkId());
            event.markProcessed();
            return true;
        } catch (Exception ex) {
            log.warn("Outbox delivery failed for {} ({}), attempt {}: {}",
                    id, event.getEventType(), event.getRetryCount() + 1, ex.toString());
            event.recordFailure(ex.toString());
            return false;
        }
    }

    public OutboxStats stats() {
        return new OutboxStats(
                repository.countByStatus(OutboxStatus.PENDING),
                repository.countByStatus(OutboxStatus.PROCESSED),
                repository.countByStatus(OutboxStatus.FAILED));
    }

    public record OutboxStats(long pending, long processed, long failed) {
    }
}
