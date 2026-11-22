package com.margomarket.monitoring.internal;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MarketSnapshotTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-22T10:00:00Z"));
    final Clock clock = new Clock() {
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return now.get(); }
    };

    static MarketSnapshot.Counts counts(long active) {
        return new MarketSnapshot.Counts(active, 0, 0, 0, 0, 0, 0, 0, null);
    }

    @SuppressWarnings("unchecked")
    void databaseReturns(long active) {
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(), any())).thenReturn(counts(active));
    }

    @Test
    void reusesCountsForThirtySecondsThenReadsAgain() {
        MarketSnapshot snapshot = new MarketSnapshot(jdbc, clock);
        databaseReturns(3);
        assertThat(snapshot.counts().activeAuctions()).isEqualTo(3);

        databaseReturns(5);
        now.set(now.get().plusSeconds(29));
        assertThat(snapshot.counts().activeAuctions()).isEqualTo(3);

        now.set(now.get().plusSeconds(2));
        assertThat(snapshot.counts().activeAuctions()).isEqualTo(5);
    }

    @Test
    void refreshAlwaysReadsTheDatabase() {
        MarketSnapshot snapshot = new MarketSnapshot(jdbc, clock);
        databaseReturns(1);
        snapshot.counts();
        databaseReturns(2);
        assertThat(snapshot.refresh().activeAuctions()).isEqualTo(2);
        assertThat(snapshot.counts().activeAuctions()).isEqualTo(2);
    }

    @Test
    void healthIsDownOnlyWhileAuctionsAreOverdue() {
        MarketSnapshot snapshot = mock(MarketSnapshot.class);
        AuctionClosingHealthIndicator health = new AuctionClosingHealthIndicator(snapshot);
        when(snapshot.refresh()).thenReturn(counts(4));
        assertThat(health.health().getStatus().getCode()).isEqualTo("UP");

        when(snapshot.refresh()).thenReturn(new MarketSnapshot.Counts(4, 2, 0, 0, 0, 0, 0, 0, Instant.parse("2026-11-22T09:50:00Z")));
        var down = health.health();
        assertThat(down.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(down.getDetails()).containsEntry("overdueAuctions", 2L).containsEntry("oldestOverdueEnd", "2026-11-22T09:50:00Z");
    }
}
