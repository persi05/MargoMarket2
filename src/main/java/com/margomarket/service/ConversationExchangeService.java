package com.margomarket.service;

import com.margomarket.dto.ConversationResponse;
import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.Conversation;
import com.margomarket.model.ConversationMessage;
import com.margomarket.model.User;
import com.margomarket.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationExchangeService {
    private final ConversationRepository conversations;
    private final ListingService listingService;
    private final ConversationAccess access;

    @Transactional
    public ConversationResponse requestIntermediary(Long id, User requester) {
        Conversation conversation = access.requireParticipant(id, requester, true);
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
        ConversationMessage message = access.saveSystem(conversation,
                "Poproszono o pośrednika. Administrator może dołączyć do rozmowy i pomóc w bezpiecznym przeprowadzeniu wymiany.");
        access.markReadThrough(conversation, requester, message.getId());
        return access.toResponse(conversation, requester);
    }

    public Page<ConversationResponse> intermediaryExchangesPage(User admin, int page) {
        access.requireAdmin(admin);
        return access.toPage(conversations.findIntermediaryExchangesPage(admin.getId(),
                PageRequest.of(Math.max(0, page - 1), 20)), admin);
    }

    @Transactional
    public ConversationResponse joinAsIntermediary(Long id, User admin) {
        access.requireAdmin(admin);
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
        ConversationMessage message = access.saveSystem(conversation, "Pośrednik dołączył do rozmowy.");
        conversation.setIntermediaryLastReadId(message.getId());
        return access.toResponse(conversation, admin);
    }

    @Transactional
    public ConversationResponse close(Long id, User requester, boolean markSold) {
        Conversation conversation = access.requireParticipant(id, requester, true);
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
            return access.toResponse(conversation, requester);
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
        ConversationMessage message = access.saveSystem(conversation,
                "Wymiana została zakończona.");
        access.markReadThrough(conversation, requester, message.getId());
        return access.toResponse(conversation, requester);
    }

    @Transactional
    public ConversationResponse requestContact(Long id, User requester) {
        Conversation conversation = access.requireParticipant(id, requester, true);
        access.requireBuyerOrSeller(conversation, requester);
        if (conversation.getClosedAt() == null) {
            throw new IllegalArgumentException("Ta rozmowa jest już otwarta.");
        }
        if (access.listingSold(conversation)) {
            throw new IllegalArgumentException("Nie można wznowić kontaktu dla sprzedanego przedmiotu.");
        }
        if (!"NONE".equals(conversation.getContactRequestStatus())) {
            throw new IllegalArgumentException("Prośba o kontakt została już rozpatrzona lub oczekuje na odpowiedź.");
        }
        conversation.setContactRequestStatus("PENDING");
        conversation.setContactRequester(requester);
        String role = requester.getId().equals(conversation.getBuyer().getId()) ? "Kupujący" : "Sprzedający";
        ConversationMessage message = access.saveSystem(conversation, role + " prosi o ponowny kontakt.");
        access.markReadThrough(conversation, requester, message.getId());
        return access.toResponse(conversation, requester);
    }

    @Transactional
    public ConversationResponse respondToContact(Long id, User responder, boolean accept) {
        Conversation conversation = access.requireParticipant(id, responder, true);
        access.requireBuyerOrSeller(conversation, responder);
        if (conversation.getClosedAt() == null || !"PENDING".equals(conversation.getContactRequestStatus())) {
            throw new IllegalArgumentException("Nie ma prośby o kontakt do rozpatrzenia.");
        }
        if (conversation.getContactRequester() == null
                || responder.getId().equals(conversation.getContactRequester().getId())) {
            throw new ForbiddenOperationException("Na prośbę może odpowiedzieć tylko druga osoba.");
        }
        if (accept && access.listingSold(conversation)) {
            throw new IllegalArgumentException("Nie można wznowić kontaktu dla sprzedanego przedmiotu.");
        }
        conversation.setContactRequestStatus(accept ? "ACCEPTED" : "REJECTED");
        if (accept) conversation.setClosedAt(null);
        ConversationMessage message = access.saveSystem(conversation, accept
                ? "Prośba o ponowny kontakt została zaakceptowana. Możecie znów pisać."
                : "Prośba o ponowny kontakt została odrzucona.");
        access.markReadThrough(conversation, responder, message.getId());
        return access.toResponse(conversation, responder);
    }

}
