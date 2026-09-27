package com.margomarket.dto;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class CommentPostingStatusTest {
    private final Instant now = Instant.parse("2026-10-24T22:30:00Z");

    @Test
    void muteEndIsAnAbsoluteInstantExactlyTwelveHoursFromServerTime() {
        var until = now.plusSeconds(12 * 60 * 60);
        var status = CommentPostingStatus.from(now, now.plusSeconds(600), until);
        assertThat(status.mutedUntil()).isEqualTo(Instant.parse("2026-10-25T10:30:00Z"));
        assertThat(status.serverTime()).isEqualTo(now);
        assertThat(status.retryAfterSeconds()).isEqualTo(43200);
        assertThat(status.muteRemainingSeconds()).isEqualTo(43200);
    }

    @Test
    void expiredMuteDoesNotHideRemainingCooldown() {
        var status = CommentPostingStatus.from(now, now.plusSeconds(60), now.minusSeconds(1));
        assertThat(status.retryAfterSeconds()).isEqualTo(60);
        assertThat(status.muteRemainingSeconds()).isZero();
        assertThat(status.mutedUntil()).isNull();
    }

    @Test
    void roundsUpAnyUnexpiredFractionAndExpiresAtTheExactDeadline() {
        assertThat(CommentPostingStatus.from(now, now.plusNanos(1), null).retryAfterSeconds()).isEqualTo(1);
        assertThat(CommentPostingStatus.from(now, now, now).retryAfterSeconds()).isZero();
        assertThat(CommentPostingStatus.from(now, null, null).retryAfterSeconds()).isZero();
    }
}
