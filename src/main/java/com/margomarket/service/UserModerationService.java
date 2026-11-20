package com.margomarket.service;

import com.margomarket.dto.PageResponse;
import com.margomarket.exception.*;
import com.margomarket.model.User;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserModerationService {
    private final JdbcTemplate jdbc;
    private final org.springframework.context.ApplicationEventPublisher events;

    public record Decision(
            long id,
            String kind,
            ModerationScope scope,
            String reason,
            Instant createdAt,
            Instant expiresAt,
            Instant revokedAt,
            String revokeReason,
            String actorName) {}

    public record Overview(List<Decision> active, PageResponse<Decision> history) {}

    public record Status(Decision accountBlock, List<Decision> active) {}

    public record Term(Long durationSeconds, Instant expiresAt, boolean permanent) {}

    public void requireAllowed(User user, ModerationScope scope) {
        var rows =
                jdbc.queryForList(
                        "SELECT scope,reason,expires_at FROM user_moderation_actions WHERE"
                            + " user_id=? AND kind='RESTRICTION' AND revoked_at IS NULL AND"
                            + " (expires_at IS NULL OR expires_at>clock_timestamp()) AND scope IN"
                            + " ('ACCOUNT',?) ORDER BY (scope='ACCOUNT') DESC,expires_at DESC NULLS"
                            + " FIRST LIMIT 1",
                        user.getId(),
                        scope.name());
        if (!rows.isEmpty()) {
            var row = rows.getFirst();
            Object end = row.get("expires_at");
            throw new UserRestrictedException(
                    ModerationScope.valueOf((String) row.get("scope")),
                    (String) row.get("reason"),
                    end == null ? null : ((java.sql.Timestamp) end).toInstant());
        }
    }

    @Transactional(readOnly = true)
    public Status status(long userId) {
        var active = active(userId);
        return new Status(
                active.stream()
                        .filter(a -> a.scope() == ModerationScope.ACCOUNT)
                        .findFirst()
                        .orElse(null),
                active);
    }

    @Transactional(readOnly = true)
    public Overview overview(long userId, int page, User admin) {
        admin(admin);
        requireUser(userId);
        int current = Math.clamp(page, 1, 1_000_000);
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM user_moderation_actions WHERE user_id=?",
                        Long.class,
                        userId);
        var history =
                decisions(
                        "WHERE a.user_id=? ORDER BY a.id DESC LIMIT 20 OFFSET ?",
                        userId,
                        (current - 1) * 20);
        return new Overview(
                active(userId),
                new PageResponse<>(
                        history,
                        current,
                        20,
                        total,
                        (int) ((total + 19) / 20),
                        current == 1,
                        current * 20L >= total));
    }

    @Transactional
    public void restrict(
            long userId, Set<ModerationScope> scopes, String reason, Term term, User actor) {
        admin(actor);
        reason = AuctionService.reason(reason);
        if (scopes == null || scopes.isEmpty() || scopes.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Wybierz co najmniej jedną blokowaną czynność");
        if (term == null
                || (term.permanent() ? 1 : 0)
                                + (term.durationSeconds() != null ? 1 : 0)
                                + (term.expiresAt() != null ? 1 : 0)
                        != 1)
            throw new IllegalArgumentException(
                    "Wybierz okres, datę zakończenia albo blokadę bezterminową");
        Instant now =
                jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
        Instant end = term.permanent() ? null : term.expiresAt();
        if (term.durationSeconds() != null) {
            if (term.durationSeconds() < 1 || term.durationSeconds() > 315360000L)
                throw new IllegalArgumentException("Podaj okres od 1 sekundy do 10 lat");
            end = now.plusSeconds(term.durationSeconds());
        }
        if (end != null && (!end.isAfter(now) || end.isAfter(now.plusSeconds(315360000L))))
            throw new IllegalArgumentException(
                    "Termin musi przypadać w przyszłości, maksymalnie za 10 lat");
        // Serialize changes affecting administrator access before locking the target account.
        if (scopes.contains(ModerationScope.ACCOUNT))
            jdbc.queryForObject("SELECT id FROM roles WHERE name='admin' FOR UPDATE", Long.class);
        lockUser(userId);
        if (userId == actor.getId())
            throw new ForbiddenOperationException("Nie możesz zablokować własnego konta");
        if (scopes.contains(ModerationScope.ACCOUNT)) {
            boolean targetAdmin =
                    Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "SELECT r.name='admin' FROM users u JOIN roles r ON"
                                        + " r.id=u.role_id WHERE u.id=?",
                                    Boolean.class,
                                    userId));
            long others =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM users u JOIN roles r ON r.id=u.role_id WHERE"
                                + " r.name='admin' AND u.id<>? AND (u.account_block_id IS NULL OR"
                                + " u.account_block_until<=clock_timestamp())",
                            Long.class,
                            userId);
            if (targetAdmin && others == 0)
                throw new ForbiddenOperationException(
                        "Nie można zablokować ostatniego dostępnego administratora");
        }
        for (var scope : scopes) {
            jdbc.update(
                    "UPDATE user_moderation_actions SET"
                        + " revoked_at=clock_timestamp(),revoked_by=?,revoke_reason='Zastąpiono"
                        + " nową decyzją' WHERE user_id=? AND scope=? AND revoked_at IS NULL",
                    actor.getId(),
                    userId,
                    scope.name());
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO"
                                + " user_moderation_actions(user_id,actor_id,kind,scope,reason,expires_at)"
                                + " VALUES (?,?,'RESTRICTION',?,?,?) RETURNING id",
                            Long.class,
                            userId,
                            actor.getId(),
                            scope.name(),
                            reason,
                            end == null ? null : AuctionService.time(end));
            if (scope == ModerationScope.ACCOUNT)
                jdbc.update(
                        "UPDATE users SET"
                            + " account_block_id=?,account_block_reason=?,account_block_until=?"
                            + " WHERE id=?",
                        id,
                        reason,
                        end == null ? null : AuctionService.time(end),
                        userId);
        }
        String labels =
                scopes.stream()
                        .sorted()
                        .map(s -> s.label)
                        .collect(java.util.stream.Collectors.joining(", "));
        notify(
                userId,
                "ACCOUNT_RESTRICTION",
                "Nałożono blokadę: "
                        + labels
                        + ". "
                        + (end == null
                                ? "Bezterminowo."
                                : "Do: "
                                        + java.time.format.DateTimeFormatter.ofPattern(
                                                        "dd.MM.yyyy HH:mm")
                                                .withZone(java.time.ZoneId.of("Europe/Warsaw"))
                                                .format(end)
                                        + ".")
                        + " Powód: "
                        + reason);
    }

    @Transactional
    public void warn(long userId, String reason, User actor) {
        admin(actor);
        reason = AuctionService.reason(reason);
        lockUser(userId);
        if (userId == actor.getId())
            throw new ForbiddenOperationException("Nie możesz upomnieć własnego konta");
        jdbc.update(
                "INSERT INTO user_moderation_actions(user_id,actor_id,kind,reason) VALUES"
                    + " (?,?,'WARNING',?)",
                userId,
                actor.getId(),
                reason);
        notify(userId, "ACCOUNT_WARNING", "Upomnienie od administracji. " + reason);
    }

    @Transactional
    public void revoke(long userId, long id, String reason, User actor) {
        admin(actor);
        reason = AuctionService.reason(reason);
        lockUser(userId);
        int changed =
                jdbc.update(
                        "UPDATE user_moderation_actions SET"
                            + " revoked_at=clock_timestamp(),revoked_by=?,revoke_reason=? WHERE"
                            + " id=? AND user_id=? AND kind='RESTRICTION' AND revoked_at IS NULL",
                        actor.getId(),
                        reason,
                        id,
                        userId);
        if (changed == 0) throw new NotFoundException("Brak blokady do cofnięcia");
        jdbc.update(
                "UPDATE users SET"
                    + " account_block_id=NULL,account_block_reason=NULL,account_block_until=NULL"
                    + " WHERE id=? AND account_block_id=?",
                userId,
                id);
        notify(
                userId,
                "ACCOUNT_RESTRICTION_REVOKED",
                "Administracja cofnęła blokadę. Uzasadnienie: " + reason);
    }

    private List<Decision> active(long id) {
        return decisions(
                "WHERE a.user_id=? AND a.kind='RESTRICTION' AND a.revoked_at IS NULL AND"
                    + " (a.expires_at IS NULL OR a.expires_at>clock_timestamp()) ORDER BY"
                    + " (a.scope='ACCOUNT') DESC,a.expires_at DESC NULLS FIRST,a.id DESC",
                id);
    }

    private List<Decision> decisions(String suffix, Object... args) {
        return jdbc.query(
                "SELECT a.*,CASE WHEN u.display_name LIKE '%@%' THEN 'Gracz '||u.id ELSE"
                    + " u.display_name END actor_name FROM user_moderation_actions a LEFT JOIN"
                    + " users u ON u.id=a.actor_id "
                        + suffix,
                (r, n) ->
                        new Decision(
                                r.getLong("id"),
                                r.getString("kind"),
                                r.getString("scope") == null
                                        ? null
                                        : ModerationScope.valueOf(r.getString("scope")),
                                r.getString("reason"),
                                r.getTimestamp("created_at").toInstant(),
                                r.getTimestamp("expires_at") == null
                                        ? null
                                        : r.getTimestamp("expires_at").toInstant(),
                                r.getTimestamp("revoked_at") == null
                                        ? null
                                        : r.getTimestamp("revoked_at").toInstant(),
                                r.getString("revoke_reason"),
                                r.getString("actor_name")),
                args);
    }

    private void notify(long userId, String type, String message) {
        jdbc.update(
                "INSERT INTO notifications(user_id,type,message,is_read,created_at) VALUES"
                    + " (?,?,?,false,clock_timestamp())",
                userId,
                type,
                message);
        events.publishEvent(new UserModerationEvents.Changed(userId));
    }

    private void admin(User user) {
        if (user == null || !user.isAdmin())
            throw new ForbiddenOperationException("Brak uprawnień administratora");
    }

    private void requireUser(long id) {
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM users WHERE id=?)", Boolean.class, id)))
            throw new NotFoundException("Użytkownik nie istnieje");
    }

    private void lockUser(long id) {
        if (jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, id)
                .isEmpty()) throw new NotFoundException("Użytkownik nie istnieje");
    }
}
