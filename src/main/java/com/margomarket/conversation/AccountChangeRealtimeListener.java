package com.margomarket.conversation;

import com.margomarket.moderation.UserModerationChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** Tells a user's open pages that their account changed and disconnects a blocked account. */
@Component
@RequiredArgsConstructor
@Slf4j
class AccountChangeRealtimeListener {
    private final JdbcTemplate jdbc;
    private final SimpMessagingTemplate messaging;
    private final ModerationWebSocketSessions sessions;

    @TransactionalEventListener
    public void changed(UserModerationChanged event) {
        try {
            String name =
                    jdbc.queryForObject(
                            "SELECT username FROM users WHERE id=?", String.class, event.userId());
            messaging.convertAndSendToUser(
                    name, "/queue/conversation-events", Map.of("kind", "ACCOUNT_CHANGED"));
            if (sessions.blocked(event.userId())) sessions.closeUser(event.userId());
        } catch (RuntimeException error) {
            log.warn("Could not publish account moderation update for {}", event.userId(), error);
        }
    }
}
