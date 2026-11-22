package com.margomarket.conversation;

import com.margomarket.user.User;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.*;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import java.util.List;

class ModerationWebSocketSessionsTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ModerationWebSocketSessions policy = new ModerationWebSocketSessions(jdbc);

    private WebSocketSession connected(String id, long userId, boolean blocked) throws Exception {
        var session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        policy.decorate(mock(WebSocketHandler.class)).afterConnectionEstablished(session);
        User user = new User();
        user.setId(userId);
        var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
        var headers = StompHeaderAccessor.create(StompCommand.CONNECTED);
        headers.setSessionId(id);
        headers.setUser(auth);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(userId))).thenReturn(blocked);
        policy.connected(
                new SessionConnectedEvent(
                        this,
                        MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()),
                        auth));
        return session;
    }

    @Test
    void closesOnlyTheBlockedUsersConnections() throws Exception {
        var target = connected("target", 7, false);
        var other = connected("other", 8, false);
        policy.closeUser(7);
        verify(target)
                .close(
                        argThat(
                                status ->
                                        status.getCode() == 1008
                                                && status.getReason().equals("ACCOUNT_BLOCKED")));
        verify(other, never()).close(any());
    }

    @Test
    void rejectsConnectionThatCompletesAfterAccountWasBlocked() throws Exception {
        var target = connected("target", 7, true);
        verify(target).close(argThat(status -> status.getCode() == 1008));
    }

    @Test
    void forgetsClosedConnections() throws Exception {
        var target = connected("target", 7, false);
        policy.decorate(mock(WebSocketHandler.class))
                .afterConnectionClosed(target, CloseStatus.NORMAL);
        policy.closeUser(7);
        verify(target, never()).close(any());
    }
}
