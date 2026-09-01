package com.rentalops.notification;

import com.rentalops.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientOrderByCreatedAtDesc(User recipient, Pageable pageable);

    Page<Notification> findByRecipientAndReadFlagFalseOrderByCreatedAtDesc(User recipient, Pageable pageable);

    long countByRecipientAndReadFlagFalse(User recipient);

    @Modifying
    @Query("update Notification n set n.readFlag = true where n.recipient = :recipient and n.readFlag = false")
    int markAllRead(@Param("recipient") User recipient);
}
