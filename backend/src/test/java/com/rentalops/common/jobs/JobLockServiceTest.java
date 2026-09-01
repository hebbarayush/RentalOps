package com.rentalops.common.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class JobLockServiceTest {

    @Autowired JobLockService jobLockService;

    @Test
    void secondAcquireFailsWhileFirstLockIsStillHeld() {
        String job = "test-job-" + System.nanoTime();
        assertThat(jobLockService.tryAcquire(job, Duration.ofMinutes(10))).isTrue();
        assertThat(jobLockService.tryAcquire(job, Duration.ofMinutes(10))).isFalse();
    }

    @Test
    void expiredLockCanBeReacquired() {
        String job = "test-job-" + System.nanoTime();
        assertThat(jobLockService.tryAcquire(job, Duration.ofMillis(1))).isTrue();
        // Lock has already expired by the time we ask again.
        assertThat(jobLockService.tryAcquire(job, Duration.ofMinutes(10))).isTrue();
    }

    @Test
    void runLockedSkipsWhenLockIsHeld() {
        String job = "test-job-" + System.nanoTime();
        int[] runs = {0};
        jobLockService.tryAcquire(job, Duration.ofMinutes(10)); // simulate another instance holding it
        jobLockService.runLocked(job, Duration.ofMinutes(10), () -> runs[0]++);
        assertThat(runs[0]).isZero();
    }
}
