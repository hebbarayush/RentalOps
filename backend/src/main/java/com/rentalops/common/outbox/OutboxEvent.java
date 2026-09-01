package com.rentalops.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per domain event that has side effects. Inserted in the same transaction as the
 * business change; drained later by {@link OutboxProcessor}.
 *
 * <p>Not a {@code BaseEntity} — the id is a client-generated {@link UUID} (assigned in the
 * constructor) rather than a DB identity, so a caller can log/track the event id before the
 * transaction even commits.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    /** How many delivery attempts before a row is parked as {@link OutboxStatus#FAILED}. */
    public static final int MAX_RETRIES = 5;

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Origin event class name — for observability only; delivery logic keys off {@link #payload}. */
    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    private Instant processedAt;

    @Column(nullable = false)
    private int retryCount = 0;

    @Column(length = 1000)
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.payload = payload;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markProcessed() {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = Instant.now();
        this.updatedAt = this.processedAt;
    }

    /** Put a parked (FAILED) row back in the queue with a fresh retry budget. */
    public void requeue() {
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Record a failed attempt. The row goes back to {@link OutboxStatus#PENDING} for another
     * try until {@link #MAX_RETRIES} is reached, then it is parked as {@link OutboxStatus#FAILED}.
     */
    public void recordFailure(String error) {
        this.retryCount++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        this.updatedAt = Instant.now();
        this.status = retryCount >= MAX_RETRIES ? OutboxStatus.FAILED : OutboxStatus.PENDING;
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public Instant getProcessedAt() { return processedAt; }
    public int getRetryCount() { return retryCount; }
    public String getLastError() { return lastError; }
}
