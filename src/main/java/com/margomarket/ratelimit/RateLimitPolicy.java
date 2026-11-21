package com.margomarket.ratelimit;

import java.time.Duration;

public enum RateLimitPolicy {
    LOGIN(Key.IP, false, 10, Duration.ofMinutes(1)),
    LOGIN_FAILURE(Key.IP, false, 5, Duration.ofMinutes(15)),
    REGISTER(Key.IP, false, 5, Duration.ofHours(1)),
    VERIFY_EMAIL(Key.IP, false, 10, Duration.ofMinutes(10)),
    CHAT_MESSAGE(Key.USER, true, 20, Duration.ofMinutes(1)),
    CONVERSATION_START(Key.USER, true, 10, Duration.ofMinutes(10)),
    REPORT(Key.USER, true, 10, Duration.ofHours(1)),
    LISTING_CREATE(Key.USER, true, 20, Duration.ofHours(1)),
    WRITE(Key.USER, true, 60, Duration.ofMinutes(1)),
    READ(Key.IP, true, 300, Duration.ofMinutes(1));

    public enum Key { IP, USER }

    private final Key key;
    private final boolean adminExempt;
    private final RateLimitProperties.Limit defaultLimit;

    RateLimitPolicy(Key key, boolean adminExempt, int capacity, Duration period) {
        this.key = key;
        this.adminExempt = adminExempt;
        this.defaultLimit = new RateLimitProperties.Limit(capacity, period);
    }

    public Key key() { return key; }
    public boolean adminExempt() { return adminExempt; }
    public RateLimitProperties.Limit defaultLimit() { return defaultLimit; }
}
