package com.rentalops.common.jobs;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobLockRepository extends JpaRepository<JobLock, String> {
    /** Atomically re-acquire an existing, expired lock. Returns 1 if this call won the race. */
    @Modifying
    @Query("update JobLock j set j.lockedUntil = :until, j.lockedBy = :by, j.updatedAt = :now "
            + "where j.jobName = :name and j.lockedUntil < :now")
    int tryReacquire(@Param("name") String name, @Param("until") Instant until,
                      @Param("by") String by, @Param("now") Instant now);
}
