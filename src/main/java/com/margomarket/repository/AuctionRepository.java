package com.margomarket.repository;

import com.margomarket.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AuctionRepository {
    private final JdbcTemplate jdbc;

    public record State(long id, String currency, long startingAmount, Instant originalEndsAt,
            Instant endsAt, Instant createdAt, String status, long version, Long highestBidId,
            Long highestAmount, Long leaderId, long bidCount) {}

    private static final String SELECT = """
        SELECT a.*, b.amount highest_amount, b.user_id leader_id,
          (SELECT count(*) FROM auction_bids h WHERE h.auction_id=a.id) bid_count
        FROM auctions a LEFT JOIN auction_bids b ON b.id=a.highest_bid_id
        """;

    public State get(long id) {
        return jdbc.query(SELECT + " WHERE a.id=?", this::map, id).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Aukcja nie istnieje"));
    }

    // All writers acquire listing first, auction second. Time is read only after both locks.
    public State lock(long id) {
        if (jdbc.queryForList("SELECT id FROM listings WHERE id=? FOR UPDATE", Long.class, id).isEmpty())
            throw new NotFoundException("Aukcja nie istnieje");
        if (jdbc.queryForList("SELECT id FROM auctions WHERE id=? FOR UPDATE", Long.class, id).isEmpty())
            throw new NotFoundException("Aukcja nie istnieje");
        return get(id);
    }

    public Instant now() {
        return jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
    }

    public List<State> getAll(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        String placeholders=String.join(",",java.util.Collections.nCopies(ids.size(),"?"));
        return jdbc.query(SELECT + " WHERE a.id IN (" + placeholders + ")",this::map,ids.toArray());
    }

    private State map(ResultSet r, int row) throws SQLException {
        return new State(r.getLong("id"), r.getString("currency"), r.getLong("starting_amount"),
                r.getObject("original_ends_at", OffsetDateTime.class).toInstant(),
                r.getObject("ends_at", OffsetDateTime.class).toInstant(),
                r.getObject("created_at", OffsetDateTime.class).toInstant(), r.getString("status"),
                r.getLong("version"), r.getObject("highest_bid_id", Long.class),
                r.getObject("highest_amount", Long.class), r.getObject("leader_id", Long.class), r.getLong("bid_count"));
    }
}
