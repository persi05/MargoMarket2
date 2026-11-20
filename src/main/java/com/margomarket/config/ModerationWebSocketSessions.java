package com.margomarket.config;

import com.margomarket.model.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ModerationWebSocketSessions implements WebSocketHandlerDecoratorFactory {
    private final JdbcTemplate jdbc;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> owners = new ConcurrentHashMap<>();

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                sessions.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status)
                    throws Exception {
                owners.remove(session.getId());
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }

    @EventListener
    public void connected(SessionConnectedEvent event) {
        var headers = StompHeaderAccessor.wrap(event.getMessage());
        if (event.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof User user) {
            owners.put(headers.getSessionId(), user.getId());
            if (blocked(user.getId())) closeUser(user.getId());
        }
    }

    public boolean blocked(long userId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM users WHERE id=? AND account_block_id IS NOT"
                            + " NULL AND (account_block_until IS NULL OR"
                            + " account_block_until>clock_timestamp()))",
                        Boolean.class,
                        userId));
    }

    public void closeUser(long userId) {
        owners.forEach(
                (id, owner) -> {
                    if (owner != userId) return;
                    WebSocketSession session = sessions.get(id);
                    if (session != null)
                        try {
                            session.close(
                                    CloseStatus.POLICY_VIOLATION.withReason("ACCOUNT_BLOCKED"));
                        } catch (Exception error) {
                            log.warn(
                                    "Could not close blocked user's WebSocket session {}",
                                    id,
                                    error);
                        }
                });
    }
}
