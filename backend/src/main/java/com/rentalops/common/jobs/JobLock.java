package com.rentalops.common.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A single row per job name, used as a cooperative mutex so a {@code @Scheduled} job runs on
 * only one backend instance at a time (see {@link JobLockService}).
 */
@Entity
@Table(name = "job_locks")
public class JobLock {
    @Id
    @Column(name = "job_name")
    private String jobName;

    @Column(nullable = false)
    private Instant lockedUntil;

    private String lockedBy;

    @Column(nullable = false)
    private Instant updatedAt;

    protected JobLock() {
    }

    public JobLock(String jobName, Instant lockedUntil, String lockedBy) {
        this.jobName = jobName;
        this.lockedUntil = lockedUntil;
        this.lockedBy = lockedBy;
        this.updatedAt = Instant.now();
    }

    public String getJobName() { return jobName; }
    public Instant getLockedUntil() { return lockedUntil; }
    public String getLockedBy() { return lockedBy; }
}
