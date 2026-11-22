package com.margomarket.ratelimit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class LoginAttemptGuardTest {
    private final FakeTime time = new FakeTime();
    private final LoginAttemptGuard guard = new LoginAttemptGuard(new RateLimiter(new RateLimitProperties(true, null), time));

    private void fail(int times, String username, String ip) {
        for (int i = 0; i < times; i++) {
            guard.ensureAllowed(username, ip);
            guard.recordFailure(username, ip);
        }
    }

    @Test
    void blocksLoginAndAddressAfterFiveFailures() {
        fail(5, "gracz", "1.1.1.1");
        assertThatThrownBy(() -> guard.ensureAllowed("gracz", "1.1.1.1"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Zbyt wiele prób. Spróbuj ponownie za 180 s.");
    }

    @Test
    void sameLoginFromAnotherAddressIsNotBlocked() {
        fail(5, "gracz", "1.1.1.1");
        assertThatCode(() -> guard.ensureAllowed("gracz", "2.2.2.2")).doesNotThrowAnyException();
        assertThatCode(() -> guard.ensureAllowed("inny", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void loginIsCaseInsensitive() {
        fail(5, "Gracz", "1.1.1.1");
        assertThatThrownBy(() -> guard.ensureAllowed(" gracz ", "1.1.1.1")).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void successfulLoginClearsFailures() {
        fail(4, "gracz", "1.1.1.1");
        guard.recordSuccess("gracz", "1.1.1.1");
        fail(4, "gracz", "1.1.1.1");
        assertThatCode(() -> guard.ensureAllowed("gracz", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void attemptsComeBackOverTime() {
        fail(5, "gracz", "1.1.1.1");
        time.advance(Duration.ofMinutes(3));
        assertThatCode(() -> guard.ensureAllowed("gracz", "1.1.1.1")).doesNotThrowAnyException();
    }
}
