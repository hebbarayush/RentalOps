package com.rentalops.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.common.events.LeaseActivatedEvent;
import com.rentalops.common.events.LeaseExpiredEvent;
import com.rentalops.common.events.LeaseExpiringEvent;
import com.rentalops.common.events.MaintenanceCreatedEvent;
import com.rentalops.common.events.MaintenanceUpdatedEvent;
import com.rentalops.common.events.RentChargeGeneratedEvent;
import com.rentalops.common.events.RentOverdueEvent;
import com.rentalops.common.outbox.OutboxEvent;
import com.rentalops.common.outbox.OutboxEventRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns domain events into <em>outbox rows</em>, decoupling the services that raise them
 * (lease, billing, housekeeping, maintenance) from notification delivery entirely.
 *
 * <p>This listener runs synchronously, inside the publisher's transaction — but it no longer
 * performs a side effect. It only writes an {@link OutboxEvent} row. So the business change and
 * the record that "a notification is owed" commit together atomically, or not at all. The
 * actual {@code Notification} is created later by {@link com.rentalops.common.outbox.OutboxProcessor},
 * off the request path, with retries. That is the point of the outbox pattern: no side effect
 * is ever fired from inside the transaction that caused it.
 */
@Component
public class NotificationEventListener {
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(OutboxEventRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onLeaseActivated(LeaseActivatedEvent e) {
        enqueue(e, e.tenantUserId(), NotificationType.LEASE_ACTIVATED,
                "Lease activated",
                "Your lease for unit %s at %s is now active (%s to %s).".formatted(
                        e.unitNumber(), e.propertyName(), e.startDate(), e.endDate()),
                "leases", e.leaseId());
    }

    @EventListener
    public void onLeaseExpired(LeaseExpiredEvent e) {
        enqueue(e, e.managerUserId(), NotificationType.LEASE_EXPIRED,
                "Lease expired",
                "The lease for unit %s at %s (tenant %s) has expired.".formatted(
                        e.unitNumber(), e.propertyName(), e.tenantName()),
                "leases", e.leaseId());
    }

    @EventListener
    public void onLeaseExpiring(LeaseExpiringEvent e) {
        enqueue(e, e.managerUserId(), NotificationType.LEASE_EXPIRING,
                "Lease expiring in " + e.daysLeft() + " day(s)",
                "The lease for unit %s at %s ends on %s.".formatted(
                        e.unitNumber(), e.propertyName(), e.endDate()),
                "leases", e.leaseId());
    }

    @EventListener
    public void onRentChargeGenerated(RentChargeGeneratedEvent e) {
        enqueue(e, e.tenantUserId(), NotificationType.RENT_DUE,
                "Rent due " + e.dueDate(),
                "Rent of %s for %s is due on %s.".formatted(e.amount(), e.propertyName(), e.dueDate()),
                "payments", e.paymentId());
    }

    @EventListener
    public void onRentOverdue(RentOverdueEvent e) {
        enqueue(e, e.tenantUserId(), NotificationType.RENT_OVERDUE,
                "Rent overdue",
                "Rent of %s for %s was due on %s and is now overdue.".formatted(
                        e.amountDue(), e.propertyName(), e.dueDate()),
                "payments", e.paymentId());
        enqueue(e, e.managerUserId(), NotificationType.RENT_OVERDUE,
                "Rent overdue",
                "%s's rent for %s (due %s) is overdue.".formatted(
                        e.tenantName(), e.propertyName(), e.dueDate()),
                "payments", e.paymentId());
    }

    @EventListener
    public void onMaintenanceCreated(MaintenanceCreatedEvent e) {
        enqueue(e, e.managerUserId(), NotificationType.MAINTENANCE_CREATED,
                "New maintenance request",
                "%s at %s — triaged as %s / %s priority.".formatted(
                        e.title(), e.propertyName(), e.category(), e.priority()),
                "maintenance-requests", e.requestId());
    }

    @EventListener
    public void onMaintenanceUpdated(MaintenanceUpdatedEvent e) {
        enqueue(e, e.tenantUserId(), NotificationType.MAINTENANCE_UPDATED,
                "Maintenance update",
                "Your request '%s' is now %s.".formatted(e.title(), e.status()),
                "maintenance-requests", e.requestId());
    }

    private void enqueue(Object domainEvent, Long recipientUserId, NotificationType type,
                         String title, String message, String linkType, Long linkId) {
        if (recipientUserId == null) {
            return; // nobody to notify (e.g. an unlinked tenant) — nothing to deliver
        }
        NotificationOutboxPayload payload = new NotificationOutboxPayload(
                recipientUserId, type, title, message, linkType, linkId);
        try {
            outbox.save(new OutboxEvent(domainEvent.getClass().getSimpleName(),
                    objectMapper.writeValueAsString(payload)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            // Serializing a 6-field record should never fail; if it somehow does, fail the whole
            // business transaction rather than commit a change with no outbox row behind it.
            throw new IllegalStateException("Could not serialize outbox payload", ex);
        }
    }
}
