package com.rentalops.common.jobs;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB-based mutex for {@code @Scheduled} jobs: in a multi-instance deployment, every instance
 * runs the same cron trigger at the same time, but only the instance that wins
 * {@link #tryAcquire} should actually execute. Losers skip this run; the lock expires on its
 * own (via {@code lockedUntil}) so a crashed holder doesn't wedge the job forever.
 */
@Service
public class JobLockService {
    private static final Logger log = LoggerFactory.getLogger(JobLockService.class);

    private final JobLockRepository repository;
    // Self-injected proxy: runLocked() must call tryAcquire() *through the Spring proxy* for its
    // @Transactional(REQUIRES_NEW) to actually apply — a plain `this.tryAcquire(...)` call from
    // inside the same bean bypasses AOP entirely (the classic self-invocation gap).
    private final JobLockService self;

    public JobLockService(JobLockRepository repository, @Lazy JobLockService self) {
        this.repository = repository;
        this.self = self;
    }

    /** Runs {@code job} only if the lock for {@code jobName} is acquired; logs and skips otherwise. */
    public void runLocked(String jobName, Duration lockFor, Runnable job) {
        if (!self.tryAcquire(jobName, lockFor)) {
            log.debug("Skipping '{}' — lock held by another instance", jobName);
            return;
        }
        job.run();
    }

    /** Own transaction, separate from the job's, so the lock is committed before the job runs. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(String jobName, Duration lockFor) {
        Instant now = Instant.now();
        Instant until = now.plus(lockFor);
        String holder = ownerId();

        int updated = repository.tryReacquire(jobName, until, holder, now);
        if (updated > 0) {
            return true;
        }
        if (repository.existsById(jobName)) {
            return false; // held by someone else, not yet expired
        }
        try {
            repository.save(new JobLock(jobName, until, holder));
            return true;
        } catch (DataIntegrityViolationException raceLost) {
            return false; // another instance inserted the row first
        }
    }

    private static String ownerId() {
        return System.getProperty("app.instance.id", ProcessHandle.current().pid() + "@" + hostname());
    }

    private static String hostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
