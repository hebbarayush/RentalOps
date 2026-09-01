package com.rentalops.notification;

/**
 * The uniform shape every notification-producing domain event is flattened into before it's
 * written to the outbox. The processor deserializes exactly this and calls
 * {@link NotificationService#notify}. Kept deliberately dumb (ids + strings, no entities) so it
 * survives being sitting in a table between the publishing transaction and delivery.
 */
public record NotificationOutboxPayload(
        Long recipientUserId,
        NotificationType type,
        String title,
        String message,
        String linkType,
        Long linkId
) {
}
