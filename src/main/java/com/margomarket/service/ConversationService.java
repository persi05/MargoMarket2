package com.margomarket.service;

import com.margomarket.dto.ChatMessageResponse;
import com.margomarket.dto.ConversationResponse;
import com.margomarket.dto.ConversationReportResponse;
import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.Conversation;
import com.margomarket.model.ConversationMessage;
import com.margomarket.model.ConversationReport;
import com.margomarket.model.Listing;
import com.margomarket.model.User;
import com.margomarket.repository.ConversationMessageRepository;
import com.margomarket.repository.ConversationRepository;
import com.margomarket.repository.ConversationReportRepository;
import com.margomarket.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {
    private static final int MESSAGE_PAGE_SIZE = 50;
    private static final int REPORT_MESSAGE_LIMIT = 50;

    private final ConversationRepository conversations;
    private final ConversationReportRepository reports;
    private final ConversationMessageRepository messages;
    private final ListingRepository listings;
    private final ListingService listingService;
    private final TextModerationService moderation;

    public List<ConversationResponse> list(User viewer) {
        return conversations.findVisibleTo(viewer.getId()).stream()
                .map(conversation -> toResponse(conversation, viewer))
                .toList();
    }

    public Page<ConversationResponse> inbox(User viewer, int limit, String scope) {
        String selectedScope = viewer.isAdmin() && Set.of("LISTINGS", "EXCHANGES").contains(scope)
                ? scope : "ALL";
        return conversations.findVisiblePage(viewer.getId(), selectedScope,
                PageRequest.of(0, Math.max(1, limit)))
                .map(conversation -> toResponse(conversation, viewer));
    }

    public List<ConversationResponse> adminChats(User admin) {
        requireAdmin(admin);
        return conversations.findAllDetailedOrderByUpdatedAtDesc().stream()
                .map(conversation -> toResponse(conversation, admin))
                .toList();
    }

    public Page<ConversationResponse> adminChatsPage(User admin, int limit) {
        requireAdmin(admin);
        return conversations.findAllDetailedPage(PageRequest.of(0, Math.max(1, limit)))
                .map(conversation -> toResponse(conversation, admin));
    }

    public ConversationResponse adminChat(Long id, User admin) {
        requireAdmin(admin);
        return toResponse(conversations.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Rozmowa nie istnieje")), admin);
    }

    public Page<ChatMessageResponse> adminChatMessages(Long id, int page, User admin) {
        requireAdmin(admin);
        Conversation conversation = conversations.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Rozmowa nie istnieje"));
        return messages.findByConversationIdOrderByIdDesc(
                id, PageRequest.of(Math.max(0, page - 1), MESSAGE_PAGE_SIZE)
        ).map(message -> toMessageResponse(message, conversation));
    }

    public long unreadCount(User viewer) {
        return messages.countUnreadForUser(viewer.getId());
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
        if (conversation.getClosedAt() != null) {
            throw new IllegalArgumentException("Ta wymiana jest zakończona.");
        }
        ConversationMessage message = saveText(conversation, buyer, body);
        conversation.setBuyerLastReadId(message.getId());
        return toResponse(conversation, buyer);
    }

    @Transactional
    public ChatMessageResponse send(Long id, String body, User sender) {
        Conversation conversation = requireParticipant(id, sender);
        if (conversation.getClosedAt() != null) {
            throw new IllegalArgumentException("Ta wymiana jest zakończona.");
        }
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
        if (conversation.getClosedAt() != null) {
            throw new IllegalArgumentException("Ta wymiana jest zakończona.");
        }
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

    public List<ConversationResponse> intermediaryExchanges(User admin) {
        requireAdmin(admin);
        return conversations.findIntermediaryExchanges(admin.getId()).stream()
                .sorted(Comparator.comparingInt(this::exchangeGroup)
                        .thenComparing(Conversation::getUpdatedAt, Comparator.reverseOrder())
                        .thenComparing(Conversation::getId, Comparator.reverseOrder()))
                .map(conversation -> toResponse(conversation, admin))
                .toList();
    }

    private int exchangeGroup(Conversation conversation) {
        if (conversation.getClosedAt() != null) return 2;
        return "REQUESTED".equals(conversation.getIntermediaryStatus()) ? 0 : 1;
    }

    @Transactional
    public ConversationResponse joinAsIntermediary(Long id, User admin) {
        requireAdmin(admin);
        Conversation conversation = conversations.findForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Rozmowa nie istnieje"));
        if (!"REQUESTED".equals(conversation.getIntermediaryStatus())) {
            throw new IllegalArgumentException("Ta rozmowa nie czeka na pośrednika.");
        }
        if (conversation.getClosedAt() != null) {
            throw new IllegalArgumentException("Ta wymiana jest zakończona.");
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

    @Transactional
    public ConversationResponse close(Long id, User requester, boolean markSold) {
        Conversation conversation = requireParticipant(id, requester, true);
        boolean closedByIntermediary = requester.isAdmin() && conversation.getIntermediary() != null
                && requester.getId().equals(conversation.getIntermediary().getId());
        if (conversation.getIntermediary() != null && !closedByIntermediary) {
            throw new ForbiddenOperationException("Po dołączeniu pośrednika tylko on może zakończyć wymianę.");
        }
        if (!requester.getId().equals(conversation.getBuyer().getId())
                && !requester.getId().equals(conversation.getSeller().getId())
                && !closedByIntermediary) {
            throw new ForbiddenOperationException("Tylko uczestnik wymiany lub jej pośrednik może ją zakończyć.");
        }
        if (conversation.getClosedAt() != null) {
            return toResponse(conversation, requester);
        }
        if (markSold && !closedByIntermediary
                && !requester.getId().equals(conversation.getSeller().getId())) {
            throw new ForbiddenOperationException("Tylko sprzedający może oznaczyć ogłoszenie jako sprzedane.");
        }
        if ((closedByIntermediary || markSold) && conversation.getListing() != null) {
            listingService.markAsSold(conversation.getListing().getId(), requester);
        }
        conversation.setClosedAt(LocalDateTime.now());
        conversation.setContactRequestStatus("NONE");
        conversation.setContactRequester(null);
        conversation.setUpdatedAt(conversation.getClosedAt());
        ConversationMessage message = saveSystem(conversation,
                "Wymiana została zakończona.");
        markReadThrough(conversation, requester, message.getId());
        return toResponse(conversation, requester);
    }

    @Transactional
    public ConversationResponse requestContact(Long id, User requester) {
        Conversation conversation = requireParticipant(id, requester, true);
        requireBuyerOrSeller(conversation, requester);
        if (conversation.getClosedAt() == null) {
            throw new IllegalArgumentException("Ta rozmowa jest już otwarta.");
        }
        if (listingSold(conversation)) {
            throw new IllegalArgumentException("Nie można wznowić kontaktu dla sprzedanego przedmiotu.");
        }
        if (!"NONE".equals(conversation.getContactRequestStatus())) {
            throw new IllegalArgumentException("Prośba o kontakt została już rozpatrzona lub oczekuje na odpowiedź.");
        }
        conversation.setContactRequestStatus("PENDING");
        conversation.setContactRequester(requester);
        String role = requester.getId().equals(conversation.getBuyer().getId()) ? "Kupujący" : "Sprzedający";
        ConversationMessage message = saveSystem(conversation, role + " prosi o ponowny kontakt.");
        markReadThrough(conversation, requester, message.getId());
        return toResponse(conversation, requester);
    }

    @Transactional
    public ConversationResponse respondToContact(Long id, User responder, boolean accept) {
        Conversation conversation = requireParticipant(id, responder, true);
        requireBuyerOrSeller(conversation, responder);
        if (conversation.getClosedAt() == null || !"PENDING".equals(conversation.getContactRequestStatus())) {
            throw new IllegalArgumentException("Nie ma prośby o kontakt do rozpatrzenia.");
        }
        if (conversation.getContactRequester() == null
                || responder.getId().equals(conversation.getContactRequester().getId())) {
            throw new ForbiddenOperationException("Na prośbę może odpowiedzieć tylko druga osoba.");
        }
        if (accept && listingSold(conversation)) {
            throw new IllegalArgumentException("Nie można wznowić kontaktu dla sprzedanego przedmiotu.");
        }
        conversation.setContactRequestStatus(accept ? "ACCEPTED" : "REJECTED");
        if (accept) conversation.setClosedAt(null);
        ConversationMessage message = saveSystem(conversation, accept
                ? "Prośba o ponowny kontakt została zaakceptowana. Możecie znów pisać."
                : "Prośba o ponowny kontakt została odrzucona.");
        markReadThrough(conversation, responder, message.getId());
        return toResponse(conversation, responder);
    }

    @Transactional
    public ConversationReportResponse report(Long id, String reason, String details, User reporter) {
        Conversation conversation = requireParticipant(id, reporter);
        if (!reporter.getId().equals(conversation.getBuyer().getId())
                && !reporter.getId().equals(conversation.getSeller().getId())) {
            throw new ForbiddenOperationException("Tylko kupujący lub sprzedający może zgłosić rozmowę.");
        }
        if (!Set.of("SCAM", "FRAUD").contains(reason)) {
            throw new IllegalArgumentException("Wybierz poprawny powód zgłoszenia.");
        }
        String cleanDetails = details == null ? "" : details.trim();
        if (cleanDetails.length() > 1000) {
            throw new IllegalArgumentException("Opis może mieć maksymalnie 1000 znaków.");
        }
        if (reports.existsByConversationIdAndReporterId(id, reporter.getId())) {
            throw new IllegalArgumentException("Ta rozmowa została już przez Ciebie zgłoszona.");
        }
        ConversationReport report = new ConversationReport();
        report.setConversation(conversation);
        report.setReporter(reporter);
        report.setReason(reason);
        report.setDetails(cleanDetails.isEmpty() ? null : moderation.masker().apply(cleanDetails));
        return toReportResponse(reports.save(report));
    }

    public List<ConversationReportResponse> reports(User admin) {
        requireAdmin(admin);
        return reports.findAllByOrderByCreatedAtDesc().stream().map(this::toReportResponse).toList();
    }

    @Transactional
    public ConversationReportResponse reviewReport(Long id, User admin) {
        requireAdmin(admin);
        ConversationReport report = reports.findById(id)
                .orElseThrow(() -> new NotFoundException("Zgłoszenie nie istnieje"));
        if (!"REVIEWED".equals(report.getStatus())) {
            report.setStatus("REVIEWED");
            report.setReviewedAt(LocalDateTime.now());
        }
        return toReportResponse(report);
    }

    public List<ChatMessageResponse> reportMessages(Long id, User admin) {
        requireAdmin(admin);
        ConversationReport report = reports.findById(id)
                .orElseThrow(() -> new NotFoundException("Zgłoszenie nie istnieje"));
        Conversation conversation = conversations.findDetailedById(report.getConversation().getId())
                .orElseThrow(() -> new NotFoundException("Rozmowa nie istnieje"));
        return messages.findByConversationIdOrderByIdDesc(conversation.getId(), PageRequest.of(0, REPORT_MESSAGE_LIMIT))
                .getContent().stream().map(message -> toMessageResponse(message, conversation)).toList();
    }

    private ConversationReportResponse toReportResponse(ConversationReport report) {
        Conversation conversation = report.getConversation();
        String reportedUserDisplayName = report.getReporter().getId().equals(conversation.getBuyer().getId())
                ? conversation.getSeller().getDisplayName() : conversation.getBuyer().getDisplayName();
        return new ConversationReportResponse(report.getId(), conversation.getId(), conversation.getItemName(),
                conversation.getBuyer().getDisplayName(), conversation.getSeller().getDisplayName(),
                report.getReporter().getDisplayName(), reportedUserDisplayName, report.getReason(), report.getDetails(),
                report.getStatus(), report.getCreatedAt(), report.getReviewedAt());
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

    private void requireBuyerOrSeller(Conversation conversation, User user) {
        if (!user.getId().equals(conversation.getBuyer().getId())
                && !user.getId().equals(conversation.getSeller().getId())) {
            throw new ForbiddenOperationException("Tylko kupujący lub sprzedający może wznowić kontakt.");
        }
    }

    private boolean listingSold(Conversation conversation) {
        return conversation.getListing() != null && conversation.getListing().getStatus() != null
                && conversation.getListing().isSold();
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

    private ChatMessageResponse toMessageResponse(ConversationMessage message, Conversation conversation) {
        Long senderId = message.getSender() == null ? null : message.getSender().getId();
        String role = senderId == null ? "system"
                : senderId.equals(conversation.getBuyer().getId()) ? "buyer"
                : senderId.equals(conversation.getSeller().getId()) ? "seller" : "intermediary";
        return new ChatMessageResponse(message.getId(), senderId,
                message.getSender() == null ? null : message.getSender().getDisplayName(), role,
                message.getKind(), message.getBody(), message.getCreatedAt());
    }
}
