package com.margomarket.service;

import com.margomarket.dto.ChatMessageResponse;
import com.margomarket.dto.ConversationResponse;
import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.Conversation;
import com.margomarket.model.ConversationMessage;
import com.margomarket.model.Listing;
import com.margomarket.model.User;
import com.margomarket.repository.ConversationMessageRepository;
import com.margomarket.repository.ConversationRepository;
import com.margomarket.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {
    private static final int MESSAGE_PAGE_SIZE = 50;

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final ListingRepository listings;
    private final TextModerationService moderation;

    public List<ConversationResponse> list(User viewer) {
        return conversations.findVisibleTo(viewer.getId()).stream()
                .map(conversation -> toResponse(conversation, viewer))
                .toList();
    }

    public long unreadCount(User viewer) {
        return conversations.findVisibleTo(viewer.getId()).stream()
                .mapToLong(conversation -> unreadCount(conversation, viewer))
                .sum();
    }

    public ConversationResponse get(Long id, User viewer) {
        return toResponse(requireParticipant(id, viewer), viewer);
    }

    @Transactional
    public ConversationResponse start(Long listingId, String body, User buyer) {
        Listing listing = listings.findByIdWithDetails(listingId)
                .orElseThrow(() -> new NotFoundException("Ogłoszenie nie istnieje"));
        if (listing.getUser().getId().equals(buyer.getId())) {
            throw new IllegalArgumentException("Nie możesz napisać do siebie w sprawie własnego ogłoszenia.");
        }
        Conversation conversation = conversations.findByListingAndBuyer(listingId, buyer.getId()).orElse(null);
        if (conversation == null) {
            if (!listing.isActive()) {
                throw new IllegalArgumentException("To ogłoszenie nie jest już aktywne.");
            }
            conversation = new Conversation();
            conversation.setListing(listing);
            conversation.setItemName(listing.getItemName());
            conversation.setBuyer(buyer);
            conversation.setSeller(listing.getUser());
            conversation = conversations.save(conversation);
        }
        ConversationMessage message = saveText(conversation, buyer, body);
        conversation.setBuyerLastReadId(message.getId());
        return toResponse(conversation, buyer);
    }

    @Transactional
    public ChatMessageResponse send(Long id, String body, User sender) {
        Conversation conversation = requireParticipant(id, sender);
        ConversationMessage message = saveText(conversation, sender, body);
        markReadThrough(conversation, sender, message.getId());
        return toMessageResponse(message, conversation);
    }

    @Transactional
    public Page<ChatMessageResponse> messages(Long id, int page, User viewer) {
        Conversation conversation = requireParticipant(id, viewer);
        Page<ChatMessageResponse> result = messages.findByConversationIdOrderByIdDesc(
                id, PageRequest.of(Math.max(0, page - 1), MESSAGE_PAGE_SIZE)
        ).map(message -> toMessageResponse(message, conversation));
        messages.findFirstByConversationIdOrderByIdDesc(id)
                .ifPresent(latest -> markReadThrough(conversation, viewer, latest.getId()));
        return result;
    }

    @Transactional
    public ConversationResponse requestIntermediary(Long id, User requester) {
        Conversation conversation = requireParticipant(id, requester, true);
        if (!requester.getId().equals(conversation.getBuyer().getId())
                && !requester.getId().equals(conversation.getSeller().getId())) {
            throw new ForbiddenOperationException("Tylko kupujący lub sprzedający może poprosić o pośrednika.");
        }
        if (!"NONE".equals(conversation.getIntermediaryStatus())) {
            throw new IllegalArgumentException("Pośrednik został już zaproszony do tej rozmowy.");
        }
        conversation.setIntermediaryStatus("REQUESTED");
        ConversationMessage message = saveSystem(conversation,
                "Poproszono o pośrednika. Administrator może dołączyć do rozmowy i pomóc w bezpiecznym przeprowadzeniu wymiany.");
        markReadThrough(conversation, requester, message.getId());
        return toResponse(conversation, requester);
    }

    public List<ConversationResponse> intermediaryRequests(User admin) {
        requireAdmin(admin);
        return conversations.findIntermediaryRequests().stream()
                .map(conversation -> toResponse(conversation, admin))
                .toList();
    }

    @Transactional
    public ConversationResponse joinAsIntermediary(Long id, User admin) {
        requireAdmin(admin);
        Conversation conversation = conversations.findForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Rozmowa nie istnieje"));
        if (!"REQUESTED".equals(conversation.getIntermediaryStatus())) {
            throw new IllegalArgumentException("Ta rozmowa nie czeka na pośrednika.");
        }
        if (admin.getId().equals(conversation.getBuyer().getId())
                || admin.getId().equals(conversation.getSeller().getId())) {
            throw new IllegalArgumentException("Uczestnik rozmowy nie może być jej pośrednikiem.");
        }
        conversation.setIntermediary(admin);
        conversation.setIntermediaryStatus("ASSIGNED");
        ConversationMessage message = saveSystem(conversation, "Pośrednik dołączył do rozmowy.");
        conversation.setIntermediaryLastReadId(message.getId());
        return toResponse(conversation, admin);
    }

    private Conversation requireParticipant(Long id, User user) {
        return requireParticipant(id, user, false);
    }

    private Conversation requireParticipant(Long id, User user, boolean forUpdate) {
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

    private void requireAdmin(User user) {
        if (!user.isAdmin()) {
            throw new ForbiddenOperationException("Tylko administrator może zostać pośrednikiem.");
        }
    }

    private ConversationMessage saveText(Conversation conversation, User sender, String body) {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty() || trimmed.length() > 2000) {
            throw new IllegalArgumentException("Wiadomość musi mieć od 1 do 2000 znaków.");
        }
        ConversationMessage message = new ConversationMessage();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setBody(moderation.masker().apply(trimmed));
        message = messages.save(message);
        conversation.setUpdatedAt(message.getCreatedAt());
        return message;
    }

    private ConversationMessage saveSystem(Conversation conversation, String body) {
        ConversationMessage message = new ConversationMessage();
        message.setConversation(conversation);
        message.setKind("SYSTEM");
        message.setBody(body);
        message = messages.save(message);
        conversation.setUpdatedAt(message.getCreatedAt());
        return message;
    }

    private void markReadThrough(Conversation conversation, User user, Long messageId) {
        if (user.getId().equals(conversation.getBuyer().getId())) {
            conversation.setBuyerLastReadId(Math.max(conversation.getBuyerLastReadId(), messageId));
        } else if (user.getId().equals(conversation.getSeller().getId())) {
            conversation.setSellerLastReadId(Math.max(conversation.getSellerLastReadId(), messageId));
        } else {
            conversation.setIntermediaryLastReadId(Math.max(conversation.getIntermediaryLastReadId(), messageId));
        }
    }

    private long unreadCount(Conversation conversation, User viewer) {
        long lastReadId = viewer.getId().equals(conversation.getBuyer().getId())
                ? conversation.getBuyerLastReadId()
                : viewer.getId().equals(conversation.getSeller().getId())
                ? conversation.getSellerLastReadId() : conversation.getIntermediaryLastReadId();
        return messages.countUnread(conversation.getId(), lastReadId, viewer.getId());
    }

    private ConversationResponse toResponse(Conversation conversation, User viewer) {
        boolean participant = viewer.getId().equals(conversation.getBuyer().getId())
                || viewer.getId().equals(conversation.getSeller().getId())
                || (conversation.getIntermediary() != null
                && viewer.getId().equals(conversation.getIntermediary().getId()));
        String lastMessage = participant ? messages.findFirstByConversationIdOrderByIdDesc(conversation.getId())
                .map(ConversationMessage::getBody).orElse("") : "";
        return new ConversationResponse(
                conversation.getId(),
                conversation.getListing() == null ? null : conversation.getListing().getId(),
                conversation.getItemName(),
                conversation.getBuyer().getId(),
                conversation.getSeller().getId(),
                conversation.getIntermediary() == null ? null : conversation.getIntermediary().getId(),
                conversation.getIntermediaryStatus(), conversation.getUpdatedAt(),
                lastMessage, participant ? unreadCount(conversation, viewer) : 0
        );
    }

    private ChatMessageResponse toMessageResponse(ConversationMessage message, Conversation conversation) {
        Long senderId = message.getSender() == null ? null : message.getSender().getId();
        String role = senderId == null ? "system"
                : senderId.equals(conversation.getBuyer().getId()) ? "buyer"
                : senderId.equals(conversation.getSeller().getId()) ? "seller" : "intermediary";
        return new ChatMessageResponse(message.getId(), senderId, role,
                message.getKind(), message.getBody(), message.getCreatedAt());
    }
}
