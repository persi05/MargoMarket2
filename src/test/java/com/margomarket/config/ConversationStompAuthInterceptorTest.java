package com.margomarket.config;

import com.margomarket.model.Role;
import com.margomarket.model.User;
import com.margomarket.security.JwtService;
import com.margomarket.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationStompAuthInterceptorTest {
    @Mock JwtService jwtService;
    @Mock UserService userService;

    @Test
    void connectRequiresValidJwtAndSetsAuthenticatedUser() {
        User user = user("user");
        when(jwtService.extractUsername("token")).thenReturn("buyer@example.com");
        when(userService.getByEmail("buyer@example.com")).thenReturn(user);
        when(jwtService.isTokenValid("token", user)).thenReturn(true);
        var accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer token");

        new ConversationStompAuthInterceptor(jwtService, userService).preSend(message(accessor), null);

        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThatThrownBy(() -> new ConversationStompAuthInterceptor(jwtService, userService)
                .preSend(message(StompHeaderAccessor.create(StompCommand.CONNECT)), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void ordinaryUserCanSubscribeOnlyToPersonalEvents() {
        var interceptor = new ConversationStompAuthInterceptor(jwtService, userService);
        var personal = subscription(user("user"), "/user/queue/conversation-events");
        var admin = subscription(user("user"), "/topic/admin-conversations");

        assertThat(interceptor.preSend(message(personal), null)).isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(message(admin), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void adminCanSubscribeToAdminEventsButClientsCannotPublish() {
        var interceptor = new ConversationStompAuthInterceptor(jwtService, userService);
        var admin = subscription(user("admin"), "/topic/admin-conversations");
        var send = StompHeaderAccessor.create(StompCommand.SEND);
        send.setDestination("/topic/admin-conversations");

        assertThat(interceptor.preSend(message(admin), null)).isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(message(send), null))
                .isInstanceOf(MessagingException.class);
    }

    private StompHeaderAccessor subscription(User user, String destination) {
        var accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        return accessor;
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private User user(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setUsername(roleName);
        user.setRole(role);
        return user;
    }
}
