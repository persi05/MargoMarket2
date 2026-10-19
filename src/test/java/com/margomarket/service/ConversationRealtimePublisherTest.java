package com.margomarket.service;

import com.margomarket.model.Conversation;
import com.margomarket.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConversationRealtimePublisherTest {
    @Mock ApplicationEventPublisher events;
    @Mock SimpMessagingTemplate messaging;

    @Test
    void publishesOneEventForParticipantsAndAdminAfterCommit() {
        Conversation conversation = new Conversation();
        conversation.setId(7L);
        conversation.setBuyer(user("buyer"));
        conversation.setSeller(user("seller"));
        ConversationRealtimePublisher publisher = new ConversationRealtimePublisher(events, messaging);
        var captured = ArgumentCaptor.forClass(ConversationRealtimePublisher.ConversationChanged.class);

        publisher.changed(conversation);
        verify(events).publishEvent(captured.capture());
        assertThat(captured.getValue().recipients()).containsExactly("buyer", "seller");

        publisher.afterCommit(captured.getValue());
        var payload = new ConversationRealtimePublisher.ConversationEventPayload(7L);
        verify(messaging).convertAndSendToUser("buyer", "/queue/conversation-events", payload);
        verify(messaging).convertAndSendToUser("seller", "/queue/conversation-events", payload);
        verify(messaging).convertAndSend("/topic/admin-conversations", payload);
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        return user;
    }
}
