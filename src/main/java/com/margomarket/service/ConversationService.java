package com.margomarket.service;

import com.margomarket.dto.ChatMessageResponse;
import com.margomarket.dto.ConversationResponse;
import com.margomarket.exception.NotFoundException;
import com.margomarket.mapper.ConversationMessageMapper;
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
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {
    private static final int MESSAGE_PAGE_SIZE = 50;

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;
    private final ListingRepository listings;
    private final TextModerationService moderation;
    private final ConversationAccess access;

    public List<ConversationResponse> list(User viewer) {
        return conversations.findVisibleTo(viewer.getId()).stream()
                .map(conversation -> access.toResponse(conversation, viewer))
                .toList();
    }

    public Page<ConversationResponse> inbox(User viewer, int limit, String scope) {
        String selectedScope = viewer.isAdmin() && Set.of("LISTINGS", "EXCHANGES").contains(scope)
                ? scope : "ALL";
        return conversations.findVisiblePage(viewer.getId(), selectedScope,
                PageRequest.of(0, Math.max(1, limit)))
                .map(conversation -> access.toResponse(conversation, viewer));
    }

    public List<ConversationResponse> adminChats(User admin) {
        access.requireAdmin(admin);
        return conversations.findAllDetailedOrderByUpdatedAtDesc().stream()
                .map(conversation -> access.toResponse(conversation, admin))
                .toList();
    }

    public Page<ConversationResponse> adminChatsPage(User admin, int page, int limit) {
        access.requireAdmin(admin);
        return conversations.findAllDetailedPage(PageRequest.of(Math.max(0, page - 1), Math.clamp(limit, 1, 50)))
                .map(conversation -> access.toResponse(conversation, admin));
    }

    public ConversationResponse adminChat(Long id, User admin) {
        access.requireAdmin(admin);
        return access.toResponse(conversations.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Rozmowa nie istnieje")), admin);
    }

    public Page<ChatMessageResponse> adminChatMessages(Long id, int page, User admin) {
        access.requireAdmin(admin);
        Conversation conversation = conversations.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Rozmowa nie istnieje"));
        return messages.findByConversationIdOrderByIdDesc(
                id, PageRequest.of(Math.max(0, page - 1), MESSAGE_PAGE_SIZE)
        ).map(message -> ConversationMessageMapper.toResponse(message, conversation));
    }

    public long unreadCount(User viewer) {
        return messages.countUnreadForUser(viewer.getId());
    }

    public ConversationResponse get(Long id, User viewer) {
        return access.toResponse(access.requireParticipant(id, viewer), viewer);
    }

    public Optional<ConversationResponse> forListing(Long listingId, User buyer) {
        return conversations.findByListingAndBuyer(listingId, buyer.getId())
                .map(conversation -> access.toResponse(conversation, buyer));
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
        if (conversation.getClosedAt() != null) {
            throw new IllegalArgumentException("Ta wymiana jest zakończona.");
        }
        ConversationMessage message = saveText(conversation, buyer, body);
        conversation.setBuyerLastReadId(message.getId());
        return access.toResponse(conversation, buyer);
    }

    @Transactional
    public ChatMessageResponse send(Long id, String body, User sender) {
        Conversation conversation = access.requireParticipant(id, sender);
        if (conversation.getClosedAt() != null) {
            throw new IllegalArgumentException("Ta wymiana jest zakończona.");
        }
        ConversationMessage message = saveText(conversation, sender, body);
        access.markReadThrough(conversation, sender, message.getId());
        return ConversationMessageMapper.toResponse(message, conversation);
    }

    @Transactional
    public Page<ChatMessageResponse> messages(Long id, int page, User viewer) {
        Conversation conversation = access.requireParticipant(id, viewer);
        Page<ChatMessageResponse> result = messages.findByConversationIdOrderByIdDesc(
                id, PageRequest.of(Math.max(0, page - 1), MESSAGE_PAGE_SIZE)
        ).map(message -> ConversationMessageMapper.toResponse(message, conversation));
        messages.findFirstByConversationIdOrderByIdDesc(id)
                .ifPresent(latest -> access.markReadThrough(conversation, viewer, latest.getId()));
        return result;
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
}
