package com.rentalops.notification;

import com.rentalops.common.BaseEntity;
import com.rentalops.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    /** Optional deep link target, e.g. "maintenance-requests" / 42. */
    private String linkType;
    private Long linkId;

    @Column(nullable = false)
    private boolean readFlag = false;

    protected Notification() {
    }

    public Notification(User recipient, NotificationType type, String title, String message, String linkType, Long linkId) {
        this.recipient = recipient;
        this.type = type;
        this.title = title;
        this.message = message;
        this.linkType = linkType;
        this.linkId = linkId;
    }

    public void markRead() {
        this.readFlag = true;
    }

    public User getRecipient() { return recipient; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getLinkType() { return linkType; }
    public Long getLinkId() { return linkId; }
    public boolean isRead() { return readFlag; }
}
