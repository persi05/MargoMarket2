package com.margomarket.ratelimit;

import com.margomarket.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Counts failed logins per login + IP, so a third party cannot lock someone else's account
 * just by guessing passwords from another address.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {
    private final RateLimiter limiter;

    public void ensureAllowed(String username, String ip) {
        long wait = limiter.secondsUntilAvailable(RateLimitPolicy.LOGIN_FAILURE, key(username, ip));
        if (wait > 0) throw new RateLimitExceededException(wait);
    }

    public void recordFailure(String username, String ip) {
        limiter.tryConsume(RateLimitPolicy.LOGIN_FAILURE, key(username, ip));
    }

    public void recordSuccess(String username, String ip) {
        limiter.reset(RateLimitPolicy.LOGIN_FAILURE, key(username, ip));
    }

    private static String key(String username, String ip) {
        return (username == null ? "" : username.trim().toLowerCase(Locale.ROOT)) + '|' + ip;
    }
}
