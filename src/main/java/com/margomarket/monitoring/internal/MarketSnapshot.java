package com.margomarket.monitoring.internal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Counts of what is going on in the market, read with one query and reused for a short while,
 * so frequent scrapes of /actuator/prometheus do not hit the database each time.
 */
@Component
class MarketSnapshot {
    static final Duration MAX_AGE = Duration.ofSeconds(30);
    /** An active auction this long past its end means the closing scheduler is not keeping up. */
    static final Duration CLOSING_GRACE = Duration.ofMinutes(2);

    record Counts(long activeAuctions, long overdueAuctions, long activeItemListings, long activeGoldListings,
                  long openConversations, long openConversationReports, long openAuctionReports,
                  long disputedFulfillments, Instant oldestOverdueEnd) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private Counts counts;
    private Instant readAt = Instant.MIN;

    @Autowired
    MarketSnapshot(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    MarketSnapshot(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    synchronized Counts counts() {
        Instant now = clock.instant();
        if (counts == null || readAt.plus(MAX_AGE).isBefore(now)) {
            counts = read();
            readAt = now;
        }
        return counts;
    }

    /** Fresh counts, bypassing the cache (health checks want the current state). */
    synchronized Counts refresh() {
        counts = read();
        readAt = clock.instant();
        return counts;
    }

    private Counts read() {
        return jdbc.queryForObject("""
                SELECT
                  (SELECT count(*) FROM auctions WHERE status='ACTIVE' AND ends_at>clock_timestamp()) AS active_auctions,
                  (SELECT count(*) FROM auctions WHERE status='ACTIVE' AND ends_at<=clock_timestamp()-make_interval(secs=>?)) AS overdue_auctions,
                  (SELECT min(ends_at) FROM auctions WHERE status='ACTIVE' AND ends_at<=clock_timestamp()-make_interval(secs=>?)) AS oldest_overdue,
                  (SELECT count(*) FROM listings l JOIN listing_statuses s ON s.id=l.status_id
                    WHERE s.name='active' AND l.listing_kind='ITEM' AND l.sale_mode='FIXED') AS item_listings,
                  (SELECT count(*) FROM listings l JOIN listing_statuses s ON s.id=l.status_id
                    WHERE s.name='active' AND l.listing_kind='GOLD') AS gold_listings,
                  (SELECT count(*) FROM conversations WHERE closed_at IS NULL) AS open_conversations,
                  (SELECT count(*) FROM conversation_reports WHERE status='OPEN') AS conversation_reports,
                  (SELECT count(*) FROM auction_reports WHERE resolved_at IS NULL) AS auction_reports,
                  (SELECT count(*) FROM auction_fulfillments WHERE status='DISPUTED') AS disputed
                """, (rs, i) -> new Counts(
                rs.getLong("active_auctions"), rs.getLong("overdue_auctions"),
                rs.getLong("item_listings"), rs.getLong("gold_listings"), rs.getLong("open_conversations"),
                rs.getLong("conversation_reports"), rs.getLong("auction_reports"), rs.getLong("disputed"),
                rs.getTimestamp("oldest_overdue") == null ? null : rs.getTimestamp("oldest_overdue").toInstant()),
                CLOSING_GRACE.toSeconds(), CLOSING_GRACE.toSeconds());
    }
}
