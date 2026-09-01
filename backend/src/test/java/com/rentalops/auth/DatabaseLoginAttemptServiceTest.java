package com.rentalops.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Same contract as {@link InMemoryLoginAttemptService}, but state now survives via the DB. */
@SpringBootTest
@Transactional
class DatabaseLoginAttemptServiceTest {

    @Autowired LoginAttemptService loginAttemptService; // wired to DatabaseLoginAttemptService (app.redis.enabled=false)

    @Test
    void blocksAfterMaxFailuresAndClearsOnReset() {
        String email = "db-guard-" + System.nanoTime() + "@example.com";
        assertThatCode(() -> loginAttemptService.assertNotBlocked(email)).doesNotThrowAnyException();

        for (int i = 0; i < 99; i++) {
            loginAttemptService.recordFailure(email);
        }
        assertThatCode(() -> loginAttemptService.assertNotBlocked(email)).doesNotThrowAnyException();

        loginAttemptService.recordFailure(email); // 100th failure — max-attempts is 100 in test yml
        assertThatThrownBy(() -> loginAttemptService.assertNotBlocked(email))
                .isInstanceOf(TooManyAttemptsException.class);

        loginAttemptService.reset(email);
        assertThatCode(() -> loginAttemptService.assertNotBlocked(email)).doesNotThrowAnyException();
    }
}
