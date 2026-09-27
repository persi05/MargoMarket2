package com.margomarket.dto;

import java.time.Duration;
import java.time.Instant;

public record CommentPostingStatus(int retryAfterSeconds, int muteRemainingSeconds,
                                   Instant serverTime, Instant mutedUntil) {
    public static CommentPostingStatus from(Instant now, Instant cooldownUntil, Instant mutedUntil) {
        int mute = remaining(now, mutedUntil);
        return new CommentPostingStatus(Math.max(mute, remaining(now, cooldownUntil)), mute,
                now, mute > 0 ? mutedUntil : null);
    }

    private static int remaining(Instant now, Instant until) {
        if (until == null || !until.isAfter(now)) return 0;
        Duration duration = Duration.between(now, until);
        return (int) Math.min(Integer.MAX_VALUE, duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0));
    }
}
