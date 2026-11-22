package com.margomarket.listing.auction;

import com.margomarket.moderation.UserModerationService;
import com.margomarket.shared.ForbiddenOperationException;
import com.margomarket.shared.NotFoundException;
import com.margomarket.shared.PageResponse;
import com.margomarket.user.User;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuctionModerationService {
    private final UserModerationService userModeration;
    private final AuctionRepository auctions;
    private final AuctionService service;
    private final JdbcTemplate jdbc;
    private final AuctionRealtimePublisher realtime;

    @Transactional
    public void invalidate(long id, long bidId, User admin, String reason) {
        admin(admin);
        reason = AuctionService.reason(reason);
        var a = auctions.lock(id);
        var bids = jdbc.queryForList("SELECT id FROM auction_bids WHERE id=? AND auction_id=?", Long.class, bidId, id);
        if (bids.isEmpty()) throw new NotFoundException("Oferta nie istnieje");
        if (jdbc.update(
                        "INSERT INTO auction_bid_invalidations(bid_id,actor_id,reason) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                        bidId,
                        admin.getId(),
                        reason)
                == 0) return;
        if ("ACTIVE".equals(a.status())) {
            Long leader = jdbc
                    .queryForList(
                            "SELECT b.id FROM auction_bids b LEFT JOIN auction_bid_invalidations i ON i.bid_id=b.id WHERE b.auction_id=? AND i.bid_id IS NULL ORDER BY b.amount DESC,b.id LIMIT 1",
                            Long.class,
                            id)
                    .stream()
                    .findFirst()
                    .orElse(null);
            jdbc.update("UPDATE auctions SET highest_bid_id=?,version=version+1 WHERE id=?", leader, id);
        } else {
            jdbc.update(
                    "UPDATE auction_fulfillments SET status='DISPUTED',updated_at=clock_timestamp() WHERE auction_id=?",
                    id);
            jdbc.update("UPDATE auctions SET version=version+1 WHERE id=?", id);
        }
        service.audit(id, admin.getId(), "BID_INVALIDATED", bidId + ": " + reason);
        realtime.changed(id, a.version() + 1);
    }

    @Transactional
    public void revoke(long id, User admin, String reason) {
        admin(admin);
        var users = jdbc.queryForList(
                "SELECT user_id FROM user_moderation_actions WHERE id=? AND scope='BIDDING'", Long.class, id);
        if (users.isEmpty()) throw new NotFoundException("Brak ograniczenia licytowania");
        userModeration.revoke(users.getFirst(), id, reason, admin);
    }

    private void resolveReport(long id, User admin, String reason) {
        admin(admin);
        if (jdbc.update(
                        "UPDATE auction_reports SET resolved_at=clock_timestamp(),resolved_by=?,resolution=? WHERE id=? AND resolved_at IS NULL",
                        admin.getId(),
                        AuctionService.reason(reason),
                        id)
                == 0) throw new NotFoundException("Brak otwartego zgłoszenia");
    }

    @Transactional
    public void reviewReport(long id, User admin, String reason, boolean invalidateBid) {
        admin(admin);
        reason = AuctionService.reason(reason);
        var reports =
                jdbc.queryForList("SELECT * FROM auction_reports WHERE id=? AND resolved_at IS NULL FOR UPDATE", id);
        if (reports.isEmpty()) throw new NotFoundException("Brak otwartego zgłoszenia");
        var report = reports.getFirst();
        if (invalidateBid) {
            if (report.get("bid_id") == null)
                throw new IllegalArgumentException("Zgłoszenie nie dotyczy konkretnej oferty");
            invalidate(
                    ((Number) report.get("auction_id")).longValue(),
                    ((Number) report.get("bid_id")).longValue(),
                    admin,
                    reason);
        }
        resolveReport(id, admin, reason);
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> page(String kind, int page, String state, User admin) {
        admin(admin);
        if (!Set.of("OPEN", "RESOLVED", "ALL").contains(state))
            throw new IllegalArgumentException("Nieznany widok zgłoszeń");
        String table = switch (kind) {
            case "reports" -> "auction_reports";
            case "restrictions" -> "user_moderation_actions";
            case "audit" -> "auction_audit";
            default -> throw new IllegalArgumentException("Nieznany rejestr");
        };
        int current = Math.clamp(page, 1, 1_000_000);
        String where = kind.equals("restrictions")
                ? " WHERE r.kind='RESTRICTION' AND r.scope='BIDDING'"
                : !kind.equals("reports") || state.equals("ALL")
                        ? ""
                        : state.equals("OPEN") ? " WHERE r.resolved_at IS NULL" : " WHERE r.resolved_at IS NOT NULL";
        long total = jdbc.queryForObject("SELECT count(*) FROM " + table + " r" + where, Long.class);
        String query = kind.equals("reports")
                ? """
            SELECT r.*,l.item_name,b.user_id bidder_id,b.amount,b.currency,
                CASE WHEN reporter.display_name LIKE '%@%' THEN 'Gracz '||reporter.id ELSE reporter.display_name END reporter_name,
                CASE WHEN bidder.display_name LIKE '%@%' THEN 'Gracz '||bidder.id ELSE bidder.display_name END bidder_name,
                (i.bid_id IS NOT NULL) bid_invalidated
            FROM auction_reports r JOIN listings l ON l.id=r.auction_id JOIN users reporter ON reporter.id=r.user_id
            LEFT JOIN auction_bids b ON b.id=r.bid_id LEFT JOIN users bidder ON bidder.id=b.user_id
            LEFT JOIN auction_bid_invalidations i ON i.bid_id=r.bid_id
            """ + where + " ORDER BY r.created_at DESC,r.id DESC LIMIT 20 OFFSET ?"
                : "SELECT r.* FROM " + table + " r ORDER BY r.id DESC LIMIT 20 OFFSET ?";
        var rows = jdbc.queryForList(query, (current - 1) * 20);
        return new PageResponse<>(
                rows, current, 20, total, (int) ((total + 19) / 20), current == 1, current * 20L >= total);
    }

    private void admin(User user) {
        if (!user.isAdmin()) throw new ForbiddenOperationException("Brak uprawnień");
    }
}
