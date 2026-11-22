package com.margomarket.ratelimit;

import com.margomarket.ratelimit.internal.RateLimitPolicy;
import com.margomarket.ratelimit.internal.RateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;

import java.time.Duration;
import java.util.Arrays;

/**
 * In-memory token buckets keyed by policy and client. Counters reset on restart, which is
 * acceptable for abuse protection on a single backend instance.
 */
public class RateLimiter {
    private final RateLimitProperties properties;
    private final TimeMeter time;
    private final Cache<String, Bucket> buckets;

    public RateLimiter(RateLimitProperties properties, TimeMeter time) {
        this.properties = properties;
        this.time = time;
        // A bucket idle for its whole period is full again, so dropping it loses nothing.
        Duration longest = Arrays.stream(RateLimitPolicy.values())
                .map(policy -> properties.limit(policy).period())
                .max(Duration::compareTo).orElseThrow();
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(longest)
                .ticker(time::currentTimeNanos)
                .maximumSize(200_000)
                .build();
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /** Takes one token; returns 0 when allowed, otherwise the seconds to wait. */
    public long tryConsume(RateLimitPolicy policy, String key) {
        if (!enabled()) return 0;
        var probe = bucket(policy, key).tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? 0 : seconds(probe.getNanosToWaitForRefill());
    }

    /** Returns 0 when a token is available, otherwise the seconds to wait; consumes nothing. */
    public long secondsUntilAvailable(RateLimitPolicy policy, String key) {
        if (!enabled()) return 0;
        var probe = bucket(policy, key).estimateAbilityToConsume(1);
        return probe.canBeConsumed() ? 0 : seconds(probe.getNanosToWaitForRefill());
    }

    public void reset(RateLimitPolicy policy, String key) {
        buckets.invalidate(policy.name() + ':' + key);
    }

    private Bucket bucket(RateLimitPolicy policy, String key) {
        return buckets.get(policy.name() + ':' + key, ignored -> {
            var limit = properties.limit(policy);
            return Bucket.builder()
                    .addLimit(l -> l.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()))
                    .withCustomTimePrecision(time)
                    .build();
        });
    }

    private static long seconds(long nanos) {
        return Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
    }
}
