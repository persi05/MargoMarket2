package com.margomarket.ratelimit;

import com.margomarket.ratelimit.internal.RateLimitPolicy;
import com.margomarket.ratelimit.internal.RateLimitProperties;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

class RateLimiterTest {
    private final FakeTime time = new FakeTime();
    private final RateLimiter limiter = new RateLimiter(new RateLimitProperties(true,
            Map.of(RateLimitPolicy.LOGIN, new RateLimitProperties.Limit(3, Duration.ofMinutes(1)))), time);

    @Test
    void rejectsOnceCapacityIsUsedAndReportsWaitTime() {
        for (int i = 0; i < 3; i++) assertThat(limiter.tryConsume(RateLimitPolicy.LOGIN, "1.1.1.1")).isZero();
        // 3 tokens per minute refill one every 20 s.
        assertThat(limiter.tryConsume(RateLimitPolicy.LOGIN, "1.1.1.1")).isEqualTo(20);
    }

    @Test
    void refillsOverTime() {
        for (int i = 0; i < 3; i++) limiter.tryConsume(RateLimitPolicy.LOGIN, "1.1.1.1");
        time.advance(Duration.ofSeconds(20));
        assertThat(limiter.tryConsume(RateLimitPolicy.LOGIN, "1.1.1.1")).isZero();
        assertThat(limiter.tryConsume(RateLimitPolicy.LOGIN, "1.1.1.1")).isPositive();
    }

    @Test
    void keysAndPoliciesAreIndependent() {
        for (int i = 0; i < 3; i++) limiter.tryConsume(RateLimitPolicy.LOGIN, "1.1.1.1");
        assertThat(limiter.tryConsume(RateLimitPolicy.LOGIN, "2.2.2.2")).isZero();
        assertThat(limiter.tryConsume(RateLimitPolicy.REGISTER, "1.1.1.1")).isZero();
    }

    @Test
    void estimateDoesNotConsumeAndResetRestoresCapacity() {
        for (int i = 0; i < 2; i++) limiter.tryConsume(RateLimitPolicy.LOGIN, "1.1.1.1");
        assertThat(limiter.secondsUntilAvailable(RateLimitPolicy.LOGIN, "1.1.1.1")).isZero();
        assertThat(limiter.secondsUntilAvailable(RateLimitPolicy.LOGIN, "1.1.1.1")).isZero();
        limiter.tryConsume(RateLimitPolicy.LOGIN, "1.1.1.1");
        assertThat(limiter.secondsUntilAvailable(RateLimitPolicy.LOGIN, "1.1.1.1")).isPositive();
        limiter.reset(RateLimitPolicy.LOGIN, "1.1.1.1");
        assertThat(limiter.secondsUntilAvailable(RateLimitPolicy.LOGIN, "1.1.1.1")).isZero();
    }

    @Test
    void disabledLimiterAllowsEverything() {
        var disabled = new RateLimiter(new RateLimitProperties(false, null), time);
        for (int i = 0; i < 50; i++) assertThat(disabled.tryConsume(RateLimitPolicy.REGISTER, "1.1.1.1")).isZero();
    }

    @Test
    void unconfiguredPoliciesUseDefaults() {
        var properties = new RateLimitProperties(true, null);
        assertThat(properties.limit(RateLimitPolicy.REGISTER))
                .isEqualTo(new RateLimitProperties.Limit(5, Duration.ofHours(1)));
    }
}
