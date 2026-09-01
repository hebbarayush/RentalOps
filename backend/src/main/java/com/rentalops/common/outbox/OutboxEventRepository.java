package com.rentalops.common.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    /** The processor's batch: oldest-first PENDING rows. */
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    long countByStatus(OutboxStatus status);

    /** Housekeeping: drop delivered rows once they're old enough to be uninteresting. */
    @Modifying
    @Query("delete from OutboxEvent e where e.status = com.rentalops.common.outbox.OutboxStatus.PROCESSED "
            + "and e.processedAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}
