package com.margomarket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AuctionBidRateLimiter {
    private final JdbcTemplate jdbc;
    @Value("${app.auctions.attempts-per-minute:30}") private int maximum;
    @Value("${app.auctions.attempt-spacing-seconds:2}") private int spacing;

    public int maximum() { return maximum; }
    public int spacing() { return spacing; }

    // Independent commit: rejected bids must consume an attempt too.
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public long attempt(long userId, long auctionId) {
        jdbc.update("INSERT INTO auction_bid_rate_limits(user_id) VALUES (?) ON CONFLICT DO NOTHING", userId);
        var row = jdbc.queryForMap("SELECT * FROM auction_bid_rate_limits WHERE user_id=? FOR UPDATE", userId);
        var now = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
        var start = jdbc.queryForObject("SELECT window_start FROM auction_bid_rate_limits WHERE user_id=?", OffsetDateTime.class, userId);
        int count = ((Number)row.get("attempts")).intValue();
        if (!now.isBefore(start.plusSeconds(60))) { start = now; count = 0; }
        jdbc.update("UPDATE auction_bid_rate_limits SET attempts=?,window_start=? WHERE user_id=?", Math.min(count + 1, maximum + 1), start, userId);
        var last = jdbc.query("SELECT attempted_at FROM auction_bid_last_attempt WHERE user_id=? AND auction_id=?",
                (r,n) -> r.getObject(1, OffsetDateTime.class), userId, auctionId);
        long wait = count >= maximum ? Math.max(1, Duration.between(now, start.plusSeconds(60)).toSeconds()+1) : 0;
        if (!last.isEmpty() && now.isBefore(last.getFirst().plusSeconds(spacing)))
            wait = Math.max(wait, Duration.between(now, last.getFirst().plusSeconds(spacing)).toSeconds()+1);
        jdbc.update("INSERT INTO auction_bid_last_attempt(user_id,auction_id,attempted_at) VALUES (?,?,?) ON CONFLICT(user_id,auction_id) DO UPDATE SET attempted_at=EXCLUDED.attempted_at", userId, auctionId, now);
        return wait;
    }
}
