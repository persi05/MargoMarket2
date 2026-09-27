package com.margomarket.service;

import com.margomarket.exception.CommentCooldownException;
import com.margomarket.exception.CommentMutedException;
import com.margomarket.dto.CommentPostingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiscussionCooldownService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public int remainingSeconds(Long listingId, Long authorId) {
        var remaining = jdbc.query("""
                SELECT GREATEST(0, CEIL(EXTRACT(EPOCH FROM (next_allowed_at - CURRENT_TIMESTAMP))))::INTEGER
                FROM discussion_cooldowns WHERE listing_id = ? AND author_id = ?
                """, (rs, row) -> rs.getInt(1), listingId, authorId);
        return remaining.isEmpty() ? 0 : remaining.getFirst();
    }

    @Transactional(readOnly = true)
    public CommentPostingStatus status(Long listingId, Long authorId) {
        return jdbc.queryForObject("""
                SELECT CURRENT_TIMESTAMP AS server_time, m.muted_until, c.next_allowed_at
                FROM (SELECT 1) AS anchor
                LEFT JOIN discussion_mutes m ON m.author_id = ?
                LEFT JOIN discussion_cooldowns c ON c.author_id = ? AND c.listing_id = ?
                """, (rs, row) -> {
                    var mute = rs.getTimestamp("muted_until");
                    var next = rs.getTimestamp("next_allowed_at");
                    return CommentPostingStatus.from(rs.getTimestamp("server_time").toInstant(),
                            next == null ? null : next.toInstant(), mute == null ? null : mute.toInstant());
                }, authorId, authorId, listingId);
    }

    @Transactional(readOnly = true)
    public CommentPostingStatus unrestrictedStatus() {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", (rs, row) ->
                CommentPostingStatus.from(rs.getTimestamp(1).toInstant(), null, null));
    }

    private int muteRemainingSeconds(Long authorId, boolean lock) {
        var remaining = jdbc.query("""
                SELECT GREATEST(0, CEIL(EXTRACT(EPOCH FROM (muted_until - CURRENT_TIMESTAMP))))::INTEGER
                FROM discussion_mutes WHERE author_id = ?
                """ + (lock ? " FOR UPDATE" : ""), (rs, row) -> rs.getInt(1), authorId);
        return remaining.isEmpty() ? 0 : remaining.getFirst();
    }

    /** Reservations join the comment transaction, so a failed save consumes neither limit. */
    @Transactional
    public void claim(Long listingId, Long authorId, boolean abusive) {
        // Serialize this author's sends across discussions; private chat does not use this service.
        jdbc.update("INSERT INTO discussion_mutes (author_id) VALUES (?) ON CONFLICT DO NOTHING", authorId);
        int mute = muteRemainingSeconds(authorId, true);
        if (mute > 0) throw new CommentMutedException(status(listingId, authorId));
        int changed = jdbc.update("""
                INSERT INTO discussion_cooldowns (listing_id, author_id, next_allowed_at)
                VALUES (?, ?, CURRENT_TIMESTAMP + INTERVAL '10 minutes')
                ON CONFLICT (listing_id, author_id) DO UPDATE SET next_allowed_at = EXCLUDED.next_allowed_at
                WHERE discussion_cooldowns.next_allowed_at <= CURRENT_TIMESTAMP
                """, listingId, authorId);
        if (changed == 0) {
            throw new CommentCooldownException(status(listingId, authorId));
        }
        if (abusive) {
            jdbc.update("UPDATE discussion_mutes SET muted_until = CURRENT_TIMESTAMP + INTERVAL '12 hours' WHERE author_id = ?", authorId);
        }
    }
}
