package com.rentalops.notification;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        String linkType,
        Long linkId,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getLinkType(), n.getLinkId(), n.isRead(), n.getCreatedAt());
    }
}
