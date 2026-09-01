package com.rentalops.notification;

import com.rentalops.auth.CurrentUserService;
import com.rentalops.common.NotFoundException;
import com.rentalops.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final CurrentUserService currentUserService;

    public NotificationService(NotificationRepository repository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    /** Fire-and-forget: create a notification for a recipient. Null recipient is a no-op. */
    @Transactional
    public void notify(User recipient, NotificationType type, String title, String message, String linkType, Long linkId) {
        if (recipient == null) {
            return;
        }
        repository.save(new Notification(recipient, type, title, message, linkType, linkId));
    }

    public void notify(User recipient, NotificationType type, String title, String message) {
        notify(recipient, type, title, message, null, null);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(boolean unreadOnly, Pageable pageable) {
        User me = currentUserService.requireCurrentUser();
        Page<Notification> page = unreadOnly
                ? repository.findByRecipientAndReadFlagFalseOrderByCreatedAtDesc(me, pageable)
                : repository.findByRecipientOrderByCreatedAtDesc(me, pageable);
        return page.map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByRecipientAndReadFlagFalse(currentUserService.requireCurrentUser());
    }

    @Transactional
    public void markRead(Long id) {
        User me = currentUserService.requireCurrentUser();
        Notification n = repository.findById(id).orElseThrow(() -> new NotFoundException("Notification not found"));
        if (!n.getRecipient().getId().equals(me.getId())) {
            throw new AccessDeniedException("Not your notification");
        }
        n.markRead();
    }

    @Transactional
    public int markAllRead() {
        return repository.markAllRead(currentUserService.requireCurrentUser());
    }
}
