package com.rentalops.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.notification.NotificationOutboxPayload;
import com.rentalops.notification.NotificationRepository;
import com.rentalops.notification.NotificationType;
import com.rentalops.user.User;
import com.rentalops.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OutboxProcessorTest {

    @Autowired OutboxProcessor processor;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;
    @Autowired ObjectMapper objectMapper;

    private User manager() {
        return userRepository.findByEmail("manager@rentalops.dev").orElseThrow();
    }

    @Test
    void pendingRowIsDeliveredAsNotificationAndMarkedProcessed() throws Exception {
        User recipient = manager();
        long before = notificationRepository.countByRecipientAndReadFlagFalse(recipient);

        var payload = new NotificationOutboxPayload(recipient.getId(), NotificationType.MAINTENANCE_CREATED,
                "Outbox test", "delivered via the outbox", "maintenance-requests", 1L);
        OutboxEvent row = outboxRepository.save(
                new OutboxEvent("TestEvent", objectMapper.writeValueAsString(payload)));

        int delivered = processor.processPendingNow();

        assertThat(delivered).isGreaterThanOrEqualTo(1);
        assertThat(outboxRepository.findById(row.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PROCESSED);
        assertThat(notificationRepository.countByRecipientAndReadFlagFalse(recipient)).isEqualTo(before + 1);
        assertThat(notificationRepository.findByRecipientOrderByCreatedAtDesc(recipient, PageRequest.of(0, 1))
                .getContent().get(0).getTitle()).isEqualTo("Outbox test");
    }

    @Test
    void unparseableRowIsRetriedThenParkedAsFailed() {
        OutboxEvent row = outboxRepository.save(new OutboxEvent("TestEvent", "{ this is not json"));

        for (int i = 0; i < OutboxEvent.MAX_RETRIES - 1; i++) {
            processor.processPendingNow();
            OutboxEvent reloaded = outboxRepository.findById(row.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(reloaded.getRetryCount()).isEqualTo(i + 1);
        }

        processor.processPendingNow(); // MAX_RETRIES-th attempt
        OutboxEvent parked = outboxRepository.findById(row.getId()).orElseThrow();
        assertThat(parked.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(parked.getLastError()).isNotBlank();

        // FAILED rows are no longer picked up...
        processor.processPendingNow();
        assertThat(outboxRepository.findById(row.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.FAILED);

        // ...until explicitly replayed.
        assertThat(processor.replayFailed()).isGreaterThanOrEqualTo(1);
        assertThat(outboxRepository.findById(row.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);
    }
}
