package com.margomarket.pricing;


import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/** Reads observed prices of a catalog item: sold and active fixed-price listings and auctions won. */
@Repository
@RequiredArgsConstructor
public class PriceSampleRepository {
    private static final int MAX_SAMPLES = 2000;

    private final JdbcTemplate jdbc;

    public List<PriceSample> findByItem(long itemId) {
        return jdbc.query("""
                SELECT l.id, l.server_id, l.enhancement_level, l.is_bound, l.sale_mode, st.name AS status,
                       lower(c.name) AS currency_name, l.price, l.created_at, l.sold_at,
                       a.status AS auction_status, a.currency AS auction_currency, a.ends_at,
                       b.amount AS bid_amount, f.status AS fulfillment_status
                FROM listings l
                JOIN listing_statuses st ON st.id = l.status_id
                JOIN currencies c ON c.id = l.currency_id
                LEFT JOIN auctions a ON a.id = l.id
                LEFT JOIN auction_bids b ON b.id = a.highest_bid_id
                LEFT JOIN auction_fulfillments f ON f.auction_id = a.id
                WHERE l.item_id = ? AND l.listing_kind = 'ITEM'
                ORDER BY l.id DESC
                LIMIT ?
                """, (rs, row) -> map(rs), itemId, MAX_SAMPLES).stream().filter(Objects::nonNull).toList();
    }

    private static PriceSample map(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        long server = rs.getLong("server_id");
        int enhancement = rs.getInt("enhancement_level");
        boolean bound = rs.getBoolean("is_bound");

        if ("AUCTION".equals(rs.getString("sale_mode"))) {
            Long bid = rs.getObject("bid_amount", Long.class);
            String fulfillment = rs.getString("fulfillment_status");
            if (bid == null || !"ENDED_WITH_WINNER".equals(rs.getString("auction_status"))
                    || "DISPUTED".equals(fulfillment) || "CANCELLED".equals(fulfillment)) return null;
            var currency = "GOLD".equals(rs.getString("auction_currency")) ? PriceSample.Currency.GOLD : PriceSample.Currency.PLN;
            Instant at = rs.getObject("ends_at", OffsetDateTime.class).toInstant();
            return new PriceSample(id, PriceSample.Source.AUCTION, server, enhancement, bound, currency, bid, at);
        }

        long price = rs.getLong("price");
        if (rs.wasNull() || price <= 0) return null;
        String status = rs.getString("status");
        PriceSample.Source source = "sold".equals(status) ? PriceSample.Source.SALE
                : "active".equals(status) ? PriceSample.Source.OFFER : null;
        if (source == null) return null;
        boolean gold = "w grze".equals(rs.getString("currency_name"));
        // Item listing prices are whole PLN or gold units.
        long amount = gold ? price : price * 100L; // PLN prices are capped far below overflow
        Timestamp at = source == PriceSample.Source.SALE && rs.getTimestamp("sold_at") != null
                ? rs.getTimestamp("sold_at") : rs.getTimestamp("created_at");
        // Listing timestamps are stored as local time of the backend.
        Instant instant = at.toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant();
        return new PriceSample(id, source, server, enhancement, bound,
                gold ? PriceSample.Currency.GOLD : PriceSample.Currency.PLN, amount, instant);
    }
}
