package com.margomarket.ratelimit;

import static com.margomarket.ratelimit.RateLimitPolicy.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

class RateLimitPolicyTest {
    private record Expected(RateLimitPolicy.Key key, boolean adminExempt, int capacity, Duration period) {}

    @Test
    void defaultsMatchTheAgreedLimits() {
        Map<RateLimitPolicy, Expected> expected = Map.of(
                LOGIN, new Expected(Key.IP, false, 10, Duration.ofMinutes(1)),
                LOGIN_FAILURE, new Expected(Key.IP, false, 5, Duration.ofMinutes(15)),
                REGISTER, new Expected(Key.IP, false, 5, Duration.ofHours(1)),
                VERIFY_EMAIL, new Expected(Key.IP, false, 10, Duration.ofMinutes(10)),
                CHAT_MESSAGE, new Expected(Key.USER, true, 20, Duration.ofMinutes(1)),
                CONVERSATION_START, new Expected(Key.USER, true, 10, Duration.ofMinutes(10)),
                REPORT, new Expected(Key.USER, true, 10, Duration.ofHours(1)),
                LISTING_CREATE, new Expected(Key.USER, true, 20, Duration.ofHours(1)),
                WRITE, new Expected(Key.USER, true, 60, Duration.ofMinutes(1)),
                READ, new Expected(Key.IP, true, 300, Duration.ofMinutes(1)));

        assertThat(expected.keySet()).containsExactlyInAnyOrder(RateLimitPolicy.values());
        expected.forEach((policy, value) -> {
            assertThat(policy.key()).as(policy.name()).isEqualTo(value.key());
            assertThat(policy.adminExempt()).as(policy.name()).isEqualTo(value.adminExempt());
            assertThat(policy.defaultLimit().capacity()).as(policy.name()).isEqualTo(value.capacity());
            assertThat(policy.defaultLimit().period()).as(policy.name()).isEqualTo(value.period());
        });
    }
}
