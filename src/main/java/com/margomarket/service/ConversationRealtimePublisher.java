package com.margomarket.service;

import com.margomarket.model.Conversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationRealtimePublisher {
    private final ApplicationEventPublisher events;
    private final SimpMessagingTemplate messaging;

    public void changed(Conversation conversation) {
        var recipients = new LinkedHashSet<String>();
        recipients.add(conversation.getBuyer().getUsername());
        recipients.add(conversation.getSeller().getUsername());
        if (conversation.getIntermediary() != null) {
            recipients.add(conversation.getIntermediary().getUsername());
        }
        events.publishEvent(new ConversationChanged(conversation.getId(), List.copyOf(recipients)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(ConversationChanged event) {
        var payload = new ConversationEventPayload(event.conversationId());
        for (String username : event.recipients()) {
            try {
                messaging.convertAndSendToUser(username, "/queue/conversation-events", payload);
            } catch (RuntimeException exception) {
                log.warn("Could not publish conversation event for {} to {}", event.conversationId(), username, exception);
            }
        }
        try {
            messaging.convertAndSend("/topic/admin-conversations", payload);
        } catch (RuntimeException exception) {
            log.warn("Could not publish admin conversation event for {}", event.conversationId(), exception);
        }
    }

    public record ConversationChanged(Long conversationId, List<String> recipients) {
    }

    public record ConversationEventPayload(Long conversationId) {
    }
}
