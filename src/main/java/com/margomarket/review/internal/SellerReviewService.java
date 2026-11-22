package com.margomarket.review.internal;

import com.margomarket.review.internal.SellerReviewDtos.*;

import com.margomarket.moderation.ModerationScope;
import com.margomarket.moderation.UserModerationService;
import com.margomarket.notification.NotificationService;
import com.margomarket.shared.ForbiddenOperationException;
import com.margomarket.shared.NotFoundException;
import com.margomarket.shared.PageResponse;
import com.margomarket.textmoderation.TextModerationService;
import com.margomarket.user.PublicNames;
import com.margomarket.user.User;
import com.margomarket.user.UserDirectory;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Opinie o sprzedawcach. Opinię może wystawić tylko kupujący z wymiany potwierdzonej przez
 * pośrednika, jedną na potwierdzenie i w ograniczonym czasie. Unieważnienie potwierdzenia lub
 * ukrycie opinii przez administratora wyłącza ją z listy i ze statystyk.
 */
@Service
@RequiredArgsConstructor
public class SellerReviewService {
    public static final Duration REVIEW_WINDOW = Duration.ofDays(30);
    public static final int MAX_COMMENT = 1000;
    private static final int PAGE_SIZE = 10;
    private static final String VISIBLE = "r.hidden_at IS NULL AND c.revoked_at IS NULL";

    private final JdbcTemplate jdbc;
    private final UserDirectory users;
    private final UserModerationService moderation;
    private final TextModerationService text;
    private final NotificationService notifications;

    @Transactional(readOnly = true)
    public List<PendingReview> pending(User buyer) {
        var rows = jdbc.queryForList("""
                SELECT c.id,c.conversation_id,c.seller_id,c.listing_id,c.confirmed_at,l.item_name FROM exchange_confirmations c
                LEFT JOIN listings l ON l.id=c.listing_id
                WHERE c.buyer_id=? AND c.seller_id IS NOT NULL AND c.revoked_at IS NULL
                  AND c.confirmed_at>clock_timestamp()-make_interval(secs=>?)
                  AND NOT EXISTS (SELECT 1 FROM seller_reviews r WHERE r.confirmation_id=c.id)
                ORDER BY c.confirmed_at DESC LIMIT 50
                """, buyer.getId(), REVIEW_WINDOW.toSeconds());
        var names = names(rows.stream().map(r -> number(r.get("seller_id"))).toList());
        return rows.stream().map(r -> {
            Instant confirmed = instant(r.get("confirmed_at"));
            long seller = number(r.get("seller_id"));
            return new PendingReview(number(r.get("id")), r.get("conversation_id") == null ? null : number(r.get("conversation_id")), seller, names.getOrDefault(seller, "Gracz " + seller), itemName(r),
                    confirmed, confirmed.plus(REVIEW_WINDOW));
        }).toList();
    }

    @Transactional
    public SellerReviewResponse create(User buyer, CreateReviewRequest request) {
        moderation.requireAllowed(buyer, ModerationScope.REVIEWS);
        if (request == null || request.confirmationId() == null) throw new IllegalArgumentException("Wskaż wymianę, której dotyczy opinia.");
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5)
            throw new IllegalArgumentException("Wybierz ocenę od 1 do 5.");
        String comment = request.comment() == null ? null : request.comment().strip();
        if (comment != null && comment.isEmpty()) comment = null;
        if (comment != null && comment.length() > MAX_COMMENT)
            throw new IllegalArgumentException("Komentarz może mieć najwyżej " + MAX_COMMENT + " znaków.");

        var rows = jdbc.queryForList("""
                SELECT c.buyer_id,c.seller_id,c.revoked_at,c.confirmed_at,l.item_name FROM exchange_confirmations c
                LEFT JOIN listings l ON l.id=c.listing_id WHERE c.id=? FOR UPDATE OF c
                """, request.confirmationId());
        if (rows.isEmpty()) throw new NotFoundException("Potwierdzona wymiana nie istnieje");
        var c = rows.getFirst();
        if (c.get("buyer_id") == null || number(c.get("buyer_id")) != buyer.getId())
            throw new ForbiddenOperationException("Opinię może wystawić tylko kupujący z potwierdzonej wymiany.");
        if (c.get("seller_id") == null) throw new IllegalArgumentException("Konto sprzedawcy nie istnieje.");
        if (c.get("revoked_at") != null) throw new IllegalArgumentException("Potwierdzenie tej wymiany zostało unieważnione.");
        if (instant(c.get("confirmed_at")).plus(REVIEW_WINDOW).isBefore(Instant.now()))
            throw new IllegalArgumentException("Czas na wystawienie opinii minął (" + REVIEW_WINDOW.toDays() + " dni od potwierdzenia wymiany).");
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM seller_reviews WHERE confirmation_id=?)", Boolean.class, request.confirmationId())))
            throw new IllegalArgumentException("Opinia o tej wymianie została już wystawiona.");

        long seller = number(c.get("seller_id"));
        String masked = comment == null ? null : text.mask(comment);
        var created = jdbc.queryForMap("INSERT INTO seller_reviews(confirmation_id,seller_id,buyer_id,rating,comment) VALUES (?,?,?,?,?) RETURNING id,created_at",
                request.confirmationId(), seller, buyer.getId(), request.rating().shortValue(), masked);
        long id = number(created.get("id"));
        String item = itemName(c);
        notifications.review(seller, "SELLER_REVIEW", "Otrzymano nową opinię (" + request.rating() + "/5) za wymianę „" + item + "”.", "seller-review:" + id);
        return new SellerReviewResponse(id, request.rating(), masked, buyer.getId(), PublicNames.name(buyer), item, instant(created.get("created_at")));
    }

    @Transactional(readOnly = true)
    public SellerReviewsResponse forSeller(Long sellerId, int page) {
        users.findVerified(sellerId).orElseThrow(() -> new NotFoundException("Profil niedostępny"));
        var stats = jdbc.queryForList("SELECT r.rating,count(*) AS n FROM seller_reviews r JOIN exchange_confirmations c ON c.id=r.confirmation_id WHERE r.seller_id=? AND " + VISIBLE + " GROUP BY r.rating", sellerId);
        long[] distribution = new long[5];
        stats.forEach(s -> distribution[(int) number(s.get("rating")) - 1] = number(s.get("n")));
        ReviewSummary summary = summary(distribution);

        int current = Math.clamp(page, 1, 1_000_000);
        long total = summary.count();
        var rows = jdbc.queryForList("""
                SELECT r.id,r.rating,r.comment,r.buyer_id,r.created_at,l.item_name FROM seller_reviews r
                JOIN exchange_confirmations c ON c.id=r.confirmation_id LEFT JOIN listings l ON l.id=c.listing_id
                WHERE r.seller_id=? AND %s ORDER BY r.created_at DESC,r.id DESC LIMIT ? OFFSET ?
                """.formatted(VISIBLE), sellerId, PAGE_SIZE, (current - 1) * PAGE_SIZE);
        var names = names(rows.stream().map(r -> number(r.get("buyer_id"))).toList());
        var masker = text.masker();
        var content = rows.stream().map(r -> new SellerReviewResponse(number(r.get("id")), (int) number(r.get("rating")),
                masker.apply((String) r.get("comment")), number(r.get("buyer_id")), names.getOrDefault(number(r.get("buyer_id")), "Gracz " + r.get("buyer_id")),
                itemName(r), instant(r.get("created_at")))).toList();
        int pages = (int) ((total + PAGE_SIZE - 1) / PAGE_SIZE);
        return new SellerReviewsResponse(summary, new PageResponse<>(content, current, PAGE_SIZE, total, pages, current == 1, (long) current * PAGE_SIZE >= total));
    }

    @Transactional
    public void hide(Long id, User admin, String reason) {
        if (admin == null || !admin.isAdmin()) throw new ForbiddenOperationException("Tylko administrator może ukryć opinię.");
        String trimmed = reason == null ? "" : reason.strip();
        if (trimmed.isEmpty() || trimmed.length() > 1000) throw new IllegalArgumentException("Podaj uzasadnienie (1–1000 znaków)");
        if (jdbc.queryForList("SELECT id FROM seller_reviews WHERE id=? FOR UPDATE", Long.class, id).isEmpty())
            throw new NotFoundException("Opinia nie istnieje");
        jdbc.update("UPDATE seller_reviews SET hidden_at=clock_timestamp(),hidden_by=?,hide_reason=? WHERE id=? AND hidden_at IS NULL", admin.getId(), trimmed, id);
    }

    static ReviewSummary summary(long[] distribution) {
        long count = Arrays.stream(distribution).sum();
        long points = 0;
        for (int i = 0; i < 5; i++) points += distribution[i] * (i + 1);
        Double average = count == 0 ? null : Math.round(points * 100.0 / count) / 100.0;
        long positive = distribution[3] + distribution[4];
        Integer positivePercent = count == 0 ? null : (int) Math.round(positive * 100.0 / count);
        return new ReviewSummary(count, average, positivePercent, Arrays.stream(distribution).boxed().toList());
    }

    private Map<Long, String> names(Collection<Long> ids) {
        return users.publicNames(ids);
    }

    private static String itemName(Map<String, Object> row) {
        Object name = row.get("item_name");
        return name == null ? "Usunięte ogłoszenie" : (String) name;
    }

    private static long number(Object value) { return ((Number) value).longValue(); }

    private static Instant instant(Object value) {
        return switch (value) {
            case Timestamp t -> t.toInstant();
            case java.time.OffsetDateTime o -> o.toInstant();
            case Instant i -> i;
            default -> throw new IllegalStateException("Nieobsługiwany typ daty: " + value);
        };
    }
}
