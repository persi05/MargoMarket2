package com.margomarket.mapper;

import com.margomarket.dto.ChatMessageResponse;
import com.margomarket.model.Conversation;
import com.margomarket.model.ConversationMessage;

public final class ConversationMessageMapper {
    private ConversationMessageMapper() {
    }

    public static ChatMessageResponse toResponse(ConversationMessage message, Conversation conversation) {
        Long senderId = message.getSender() == null ? null : message.getSender().getId();
        String role = senderId == null ? "system"
                : senderId.equals(conversation.getBuyer().getId()) ? "buyer"
                : senderId.equals(conversation.getSeller().getId()) ? "seller" : "intermediary";
        return new ChatMessageResponse(message.getId(), senderId,
                message.getSender() == null ? null : message.getSender().getDisplayName(), role,
                message.getKind(), message.getBody(), message.getCreatedAt());
    }
}
