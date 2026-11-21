package com.margomarket.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

/**
 * Limits default to {@link RateLimitPolicy}; override one with e.g.
 * {@code app.rate-limit.limits.chat-message.capacity=30} and {@code ...period=1m}.
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@DefaultValue("true") boolean enabled, Map<RateLimitPolicy, Limit> limits) {

    public record Limit(int capacity, Duration period) {
        public Limit {
            if (capacity <= 0 || period == null || period.isZero() || period.isNegative())
                throw new IllegalArgumentException("Rate limit needs a positive capacity and period");
        }
    }

    public RateLimitProperties {
        limits = limits == null ? Map.of() : Map.copyOf(limits);
    }

    public Limit limit(RateLimitPolicy policy) {
        return limits.getOrDefault(policy, policy.defaultLimit());
    }
}
