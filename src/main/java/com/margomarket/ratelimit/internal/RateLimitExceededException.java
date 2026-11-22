package com.margomarket.ratelimit.internal;

import java.util.LinkedHashMap;
import java.util.Map;

public class RateLimitExceededException extends RuntimeException {
    public static final String CODE = "RATE_LIMITED";
    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Zbyt wiele prób. Spróbuj ponownie za " + retryAfterSeconds + " s.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Map<String, Object> response() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 429);
        body.put("error", "Too Many Requests");
        body.put("code", CODE);
        body.put("message", getMessage());
        body.put("retryAfterSeconds", retryAfterSeconds);
        return body;
    }
}
