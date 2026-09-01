package com.rentalops.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    @Test
    void blocksAfterMaxFailuresAndClearsOnReset() {
        LoginAttemptService svc = new InMemoryLoginAttemptService(3, 15);
        String email = "user@example.com";

        assertThatCode(() -> svc.assertNotBlocked(email)).doesNotThrowAnyException();
        svc.recordFailure(email);
        svc.recordFailure(email);
        assertThatCode(() -> svc.assertNotBlocked(email)).doesNotThrowAnyException();

        svc.recordFailure(email);
        assertThatThrownBy(() -> svc.assertNotBlocked(email))
                .isInstanceOf(TooManyAttemptsException.class);

        svc.reset(email);
        assertThatCode(() -> svc.assertNotBlocked(email)).doesNotThrowAnyException();
    }

    @Test
    void isolatesPerEmail() {
        LoginAttemptService svc = new InMemoryLoginAttemptService(1, 15);
        svc.recordFailure("a@example.com");
        assertThatThrownBy(() -> svc.assertNotBlocked("a@example.com"))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> svc.assertNotBlocked("b@example.com")).doesNotThrowAnyException();
    }
}
