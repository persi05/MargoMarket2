package com.margomarket.config;

import com.margomarket.model.User;
import com.margomarket.security.JwtService;
import com.margomarket.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
@RequiredArgsConstructor
class ConversationStompAuthInterceptor implements ChannelInterceptor {
    private final JwtService jwtService;
    private final UserService userService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;

        if (accessor.getCommand() == StompCommand.CONNECT) {
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new MessagingException("Authentication required");
            }
            try {
                String token = authorization.substring(7);
                User user = userService.getByEmail(jwtService.extractUsername(token));
                if (!jwtService.isTokenValid(token, user)) throw new MessagingException("Invalid token");
                accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
            } catch (RuntimeException exception) {
                throw new MessagingException("Invalid token", exception);
            }
        } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            Principal principal = accessor.getUser();
            if (!(principal instanceof UsernamePasswordAuthenticationToken authentication)
                    || !(authentication.getPrincipal() instanceof User user)) {
                throw new MessagingException("Authentication required");
            }
            String destination = accessor.getDestination();
            boolean personal = "/user/queue/conversation-events".equals(destination);
            boolean admin = user.isAdmin() && "/topic/admin-conversations".equals(destination);
            if (!personal && !admin) throw new MessagingException("Subscription not allowed");
        } else if (accessor.getCommand() == StompCommand.SEND) {
            throw new MessagingException("Client messages are not allowed");
        }
        return message;
    }
}
