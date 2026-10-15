package com.margomarket.service;

import com.margomarket.dto.ConversationResponse;
import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.Conversation;
import com.margomarket.model.ConversationMessage;
import com.margomarket.model.User;
import com.margomarket.repository.ConversationMessageRepository;
import com.margomarket.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ConversationAccess {
    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;

    Conversation requireParticipant(Long id, User user) {
        return requireParticipant(id, user, false);
    }

    Conversation requireParticipant(Long id, User user, boolean forUpdate) {
        Conversation conversation = (forUpdate ? conversations.findForUpdate(id) : conversations.findDetailedById(id))
                .orElseThrow(() -> new NotFoundException("Rozmowa nie istnieje"));
        Long userId = user.getId();
        if (!userId.equals(conversation.getBuyer().getId())
                && !userId.equals(conversation.getSeller().getId())
                && (conversation.getIntermediary() == null || !userId.equals(conversation.getIntermediary().getId()))) {
            throw new ForbiddenOperationException("Nie masz dostępu do tej rozmowy.");
        }
        return conversation;
    }

    void requireAdmin(User user) {
        if (!user.isAdmin()) {
            throw new ForbiddenOperationException("Tylko administrator może zostać pośrednikiem.");
        }
    }

    void requireBuyerOrSeller(Conversation conversation, User user) {
        if (!user.getId().equals(conversation.getBuyer().getId())
                && !user.getId().equals(conversation.getSeller().getId())) {
            throw new ForbiddenOperationException("Tylko kupujący lub sprzedający może wznowić kontakt.");
        }
    }

    boolean listingSold(Conversation conversation) {
        return conversation.getListing() != null && conversation.getListing().getStatus() != null
                && conversation.getListing().isSold();
    }

    ConversationMessage saveSystem(Conversation conversation, String body) {
        ConversationMessage message = new ConversationMessage();
        message.setConversation(conversation);
        message.setKind("SYSTEM");
        message.setBody(body);
        message = messages.save(message);
        conversation.setUpdatedAt(message.getCreatedAt());
        return message;
    }

    void markReadThrough(Conversation conversation, User user, Long messageId) {
        if (user.getId().equals(conversation.getBuyer().getId())) {
            conversation.setBuyerLastReadId(Math.max(conversation.getBuyerLastReadId(), messageId));
        } else if (user.getId().equals(conversation.getSeller().getId())) {
            conversation.setSellerLastReadId(Math.max(conversation.getSellerLastReadId(), messageId));
        } else {
            conversation.setIntermediaryLastReadId(Math.max(conversation.getIntermediaryLastReadId(), messageId));
        }
    }

    ConversationResponse toResponse(Conversation conversation, User viewer) {
        boolean participant = viewer.getId().equals(conversation.getBuyer().getId())
                || viewer.getId().equals(conversation.getSeller().getId())
                || (conversation.getIntermediary() != null
                && viewer.getId().equals(conversation.getIntermediary().getId()));
        String lastMessage = (participant || viewer.isAdmin()) ? messages.findFirstByConversationIdOrderByIdDesc(conversation.getId())
                .map(ConversationMessage::getBody).orElse("") : "";
        return new ConversationResponse(
                conversation.getId(),
                conversation.getListing() == null ? null : conversation.getListing().getId(),
                conversation.getItemName(),
                conversation.getBuyer().getId(),
                conversation.getBuyer().getDisplayName(),
                conversation.getSeller().getId(),
                conversation.getSeller().getDisplayName(),
                conversation.getIntermediary() == null ? null : conversation.getIntermediary().getId(),
                conversation.getIntermediary() == null ? null : conversation.getIntermediary().getDisplayName(),
                conversation.getIntermediaryStatus(), conversation.getUpdatedAt(),
                lastMessage, participant ? unreadCount(conversation, viewer) : 0, conversation.getClosedAt(),
                conversation.getContactRequestStatus(),
                conversation.getContactRequester() == null ? null : conversation.getContactRequester().getId(),
                !listingSold(conversation)
        );
    }

    private long unreadCount(Conversation conversation, User viewer) {
        long lastReadId = viewer.getId().equals(conversation.getBuyer().getId())
                ? conversation.getBuyerLastReadId()
                : viewer.getId().equals(conversation.getSeller().getId())
                ? conversation.getSellerLastReadId() : conversation.getIntermediaryLastReadId();
        return messages.countUnread(conversation.getId(), lastReadId, viewer.getId());
    }
}
