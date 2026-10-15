package com.margomarket.service;

import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.model.Conversation;
import com.margomarket.model.ConversationMessage;
import com.margomarket.model.Listing;
import com.margomarket.model.ListingStatus;
import com.margomarket.model.Role;
import com.margomarket.model.User;
import com.margomarket.repository.ConversationMessageRepository;
import com.margomarket.repository.ConversationRepository;
import com.margomarket.repository.ConversationReportRepository;
import com.margomarket.repository.ListingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {
    @Mock ConversationRepository conversations;
    @Mock ConversationReportRepository reports;
    @Mock ConversationMessageRepository messages;
    @Mock ListingRepository listings;
    @Mock ListingService listingService;
    @Mock TextModerationService moderation;
    @InjectMocks ConversationService service;

    @Test
    void unrelatedUserCannotReadOrSendPrivateMessages() {
        Conversation conversation = conversation(user(1L, "user"), user(2L, "user"));
        when(conversations.findDetailedById(7L)).thenReturn(Optional.of(conversation));
        User outsider = user(3L, "user");

        assertThatThrownBy(() -> service.get(7L, outsider)).isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.send(7L, "Cześć", outsider)).isInstanceOf(ForbiddenOperationException.class);
        verify(messages, never()).save(any());
    }

    @Test
    void sellerCannotStartConversationWithOwnListing() {
        User seller = user(2L, "user");
        Listing listing = new Listing();
        listing.setUser(seller);
        when(listings.findByIdWithDetails(15L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> service.start(15L, "Cześć", seller))
                .isInstanceOf(IllegalArgumentException.class);
        verify(conversations, never()).save(any());
    }

    @Test
    void participantCanRequestIntermediaryOnlyOnce() {
        User buyer = user(1L, "user");
        Conversation conversation = conversation(buyer, user(2L, "user"));
        when(conversations.findForUpdate(7L)).thenReturn(Optional.of(conversation));
        when(messages.save(any(ConversationMessage.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        var response = service.requestIntermediary(7L, buyer);

        assertThat(response.intermediaryStatus()).isEqualTo("REQUESTED");
        assertThat(response.buyerDisplayName()).isEqualTo("Gracz1");
        assertThat(response.sellerDisplayName()).isEqualTo("Gracz2");
        assertThat(conversation.getBuyerLastReadId()).isEqualTo(20L);
        assertThatThrownBy(() -> service.requestIntermediary(7L, buyer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void messageResponseContainsSenderDisplayName() {
        User buyer = user(1L, "user");
        User seller = user(2L, "user");
        when(conversations.findDetailedById(7L)).thenReturn(Optional.of(conversation(buyer, seller)));
        when(moderation.masker()).thenReturn(java.util.function.UnaryOperator.identity());
        when(messages.save(any(ConversationMessage.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        var response = service.send(7L, "Cześć", seller);

        assertThat(response.senderDisplayName()).isEqualTo("Gracz2");
        assertThat(response.senderRole()).isEqualTo("seller");
    }

    @Test
    void conversationInitiallyLoadsFiftyLatestMessages() {
        User buyer = user(1L, "user");
        when(conversations.findDetailedById(7L)).thenReturn(Optional.of(conversation(buyer, user(2L, "user"))));
        when(messages.findByConversationIdOrderByIdDesc(7L, PageRequest.of(0, 50)))
                .thenReturn(Page.empty());

        service.messages(7L, 1, buyer);

        verify(messages).findByConversationIdOrderByIdDesc(7L, PageRequest.of(0, 50));
    }

    @Test
    void inboxInitiallyFetchesOnlyTenConversations() {
        User buyer = user(1L, "user");
        Conversation conversation = conversation(buyer, user(2L, "user"));
        PageRequest firstTen = PageRequest.of(0, 10);
        when(conversations.findVisiblePage(1L, "ALL", firstTen))
                .thenReturn(new PageImpl<>(List.of(conversation), firstTen, 25));

        var page = service.inbox(buyer, 10, "EXCHANGES");

        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getContent()).extracting(response -> response.id()).containsExactly(7L);
        verify(conversations).findVisiblePage(1L, "ALL", firstTen);
    }

    @Test
    void unreadBadgeUsesOneAggregateQuery() {
        User buyer = user(1L, "user");
        when(messages.countUnreadForUser(1L)).thenReturn(4L);

        assertThat(service.unreadCount(buyer)).isEqualTo(4L);

        verify(messages).countUnreadForUser(1L);
        verify(conversations, never()).findVisibleTo(any());
    }

    @Test
    void onlyAdminCanJoinRequestedConversation() {
        Conversation conversation = conversation(user(1L, "user"), user(2L, "user"));
        conversation.setIntermediaryStatus("REQUESTED");
        when(conversations.findForUpdate(7L)).thenReturn(Optional.of(conversation));
        when(messages.save(any(ConversationMessage.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        assertThatThrownBy(() -> service.joinAsIntermediary(7L, user(3L, "user")))
                .isInstanceOf(ForbiddenOperationException.class);
        User admin = user(4L, "admin");
        var response = service.joinAsIntermediary(7L, admin);
        assertThat(response.intermediaryStatus()).isEqualTo("ASSIGNED");
        assertThat(response.intermediaryId()).isEqualTo(4L);
        assertThat(conversation.getIntermediaryLastReadId()).isEqualTo(20L);
    }

    @Test
    void closingExchangeKeepsConversationVisibleToParticipants() {
        User buyer = user(1L, "user");
        Conversation conversation = conversation(buyer, user(2L, "user"));
        when(conversations.findForUpdate(7L)).thenReturn(Optional.of(conversation));
        when(conversations.findDetailedById(7L)).thenReturn(Optional.of(conversation));
        when(messages.save(any(ConversationMessage.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        var closed = service.close(7L, buyer, false);

        assertThat(closed.closedAt()).isNotNull();
        assertThatThrownBy(() -> service.send(7L, "Jeszcze wiadomość", buyer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.get(7L, buyer).closedAt()).isNotNull();
        when(conversations.findVisibleTo(1L)).thenReturn(List.of(conversation));
        assertThat(service.list(buyer)).extracting(response -> response.id()).containsExactly(7L);
        verify(listingService, never()).markAsSold(any(), any());
    }

    @Test
    void intermediaryAdminCanCloseAndReadFinishedExchange() {
        User admin = user(3L, "admin");
        Conversation conversation = conversation(user(1L, "user"), user(2L, "user"));
        conversation.setIntermediary(admin);
        conversation.setIntermediaryStatus("ASSIGNED");
        Listing listing = new Listing();
        listing.setId(15L);
        conversation.setListing(listing);
        when(conversations.findForUpdate(7L)).thenReturn(Optional.of(conversation));
        when(conversations.findDetailedById(7L)).thenReturn(Optional.of(conversation));
        when(messages.save(any(ConversationMessage.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        assertThat(service.close(7L, admin, false).closedAt()).isNotNull();
        assertThat(service.get(7L, admin).closedAt()).isNotNull();
        verify(listingService).markAsSold(15L, admin);
    }

    @Test
    void onlyAssignedIntermediaryCanCloseExchangeAfterJoining() {
        User buyer = user(1L, "user");
        User seller = user(2L, "user");
        User admin = user(3L, "admin");
        Conversation conversation = conversation(buyer, seller);
        conversation.setIntermediary(admin);
        conversation.setIntermediaryStatus("ASSIGNED");
        when(conversations.findForUpdate(7L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.close(7L, buyer, false))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.close(7L, seller, true))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(conversation.getClosedAt()).isNull();
        verify(listingService, never()).markAsSold(any(), any());
    }

    @Test
    void sellerCanMarkListingSoldWhenClosingExchange() {
        User seller = user(2L, "user");
        Conversation conversation = conversation(user(1L, "user"), seller);
        Listing listing = new Listing();
        listing.setId(15L);
        conversation.setListing(listing);
        when(conversations.findForUpdate(7L)).thenReturn(Optional.of(conversation));
        when(messages.save(any(ConversationMessage.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        assertThat(service.close(7L, seller, true).closedAt()).isNotNull();
        verify(listingService).markAsSold(15L, seller);
    }

    @Test
    void buyerCannotMarkSomeoneElsesListingSold() {
        User buyer = user(1L, "user");
        Conversation conversation = conversation(buyer, user(2L, "user"));
        when(conversations.findForUpdate(7L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.close(7L, buyer, true))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThat(conversation.getClosedAt()).isNull();
        verify(listingService, never()).markAsSold(any(), any());
    }

    @Test
    void adminExchangeListOrdersRequestsThenActiveThenFinished() {
        User admin = user(3L, "admin");
        Conversation requested = conversation(user(1L, "user"), user(2L, "user"));
        requested.setId(1L);
        requested.setIntermediaryStatus("REQUESTED");
        Conversation active = conversation(user(4L, "user"), user(5L, "user"));
        active.setId(2L);
        active.setIntermediary(admin);
        active.setIntermediaryStatus("ASSIGNED");
        Conversation finished = conversation(user(6L, "user"), user(7L, "user"));
        finished.setId(3L);
        finished.setIntermediary(admin);
        finished.setIntermediaryStatus("ASSIGNED");
        finished.setClosedAt(LocalDateTime.now());
        when(conversations.findIntermediaryExchanges(3L)).thenReturn(List.of(finished, active, requested));

        assertThat(service.intermediaryExchanges(admin)).extracting(response -> response.id())
                .containsExactly(1L, 2L, 3L);
        assertThatThrownBy(() -> service.intermediaryExchanges(user(8L, "user")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void adminCanSeeChatsWithoutJoiningThem() {
        User admin = user(3L, "admin");
        Conversation conversation = conversation(user(1L, "user"), user(2L, "user"));
        when(conversations.findAllDetailedOrderByUpdatedAtDesc()).thenReturn(List.of(conversation));
        when(conversations.findDetailedById(7L)).thenReturn(Optional.of(conversation));

        assertThat(service.adminChats(admin)).extracting(response -> response.id()).containsExactly(7L);
        assertThat(service.adminChat(7L, admin).id()).isEqualTo(7L);
        assertThatThrownBy(() -> service.get(7L, admin))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.adminChats(user(4L, "user")))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.adminChat(7L, user(4L, "user")))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.adminChatMessages(7L, 1, user(4L, "user")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void outsiderCannotReportPrivateConversation() {
        Conversation conversation = conversation(user(1L, "user"), user(2L, "user"));
        when(conversations.findDetailedById(7L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.report(7L, "SCAM", "", user(3L, "user")))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(reports, never()).save(any());
    }

    @Test
    void duplicateReportIsRejected() {
        User buyer = user(1L, "user");
        when(conversations.findDetailedById(7L)).thenReturn(Optional.of(conversation(buyer, user(2L, "user"))));
        when(reports.existsByConversationIdAndReporterId(7L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.report(7L, "SCAM", "", buyer))
                .isInstanceOf(IllegalArgumentException.class);
        verify(reports, never()).save(any());
    }

    @Test
    void reportIdentifiesOtherConversationParticipant() {
        User buyer = user(1L, "user");
        when(conversations.findDetailedById(7L)).thenReturn(Optional.of(conversation(buyer, user(2L, "user"))));
        when(moderation.masker()).thenReturn(text -> text.replace("sytuacji", "********"));
        when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var report = service.report(7L, "FRAUD", "Opis sytuacji", buyer);

        assertThat(report.reporterDisplayName()).isEqualTo("Gracz1");
        assertThat(report.reportedUserDisplayName()).isEqualTo("Gracz2");
        assertThat(report.reason()).isEqualTo("FRAUD");
        assertThat(report.details()).isEqualTo("Opis ********");
    }

    @Test
    void onlyAdminCanListReportsAndReadReportedMessages() {
        User ordinaryUser = user(1L, "user");

        assertThatThrownBy(() -> service.reports(ordinaryUser))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.reportMessages(5L, ordinaryUser))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void closedConversationCannotBeRestartedFromListing() {
        User buyer = user(1L, "user");
        User seller = user(2L, "user");
        Listing listing = new Listing();
        listing.setUser(seller);
        Conversation conversation = conversation(buyer, seller);
        conversation.setClosedAt(LocalDateTime.now());
        when(listings.findByIdWithDetails(15L)).thenReturn(Optional.of(listing));
        when(conversations.findByListingAndBuyer(15L, 1L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.start(15L, "Nowa wiadomość", buyer))
                .isInstanceOf(IllegalArgumentException.class);
        verify(messages, never()).save(any());
    }

    @Test
    void otherParticipantCanAcceptContactRequestAndReopenConversation() {
        User buyer = user(1L, "user");
        User seller = user(2L, "user");
        Conversation conversation = conversation(buyer, seller);
        conversation.setClosedAt(LocalDateTime.now());
        when(conversations.findForUpdate(7L)).thenReturn(Optional.of(conversation));
        when(messages.save(any(ConversationMessage.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        var requested = service.requestContact(7L, buyer);
        assertThat(requested.contactRequestStatus()).isEqualTo("PENDING");
        assertThat(requested.contactRequesterId()).isEqualTo(1L);
        assertThat(conversation.getClosedAt()).isNotNull();
        assertThatThrownBy(() -> service.respondToContact(7L, buyer, true))
                .isInstanceOf(ForbiddenOperationException.class);

        var accepted = service.respondToContact(7L, seller, true);
        assertThat(accepted.contactRequestStatus()).isEqualTo("ACCEPTED");
        assertThat(accepted.closedAt()).isNull();
        assertThat(conversation.getSellerLastReadId()).isEqualTo(20L);
    }

    @Test
    void rejectedContactRequestKeepsConversationClosed() {
        User buyer = user(1L, "user");
        User seller = user(2L, "user");
        Conversation conversation = conversation(buyer, seller);
        conversation.setClosedAt(LocalDateTime.now());
        when(conversations.findForUpdate(7L)).thenReturn(Optional.of(conversation));
        when(messages.save(any(ConversationMessage.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        service.requestContact(7L, buyer);
        var rejected = service.respondToContact(7L, seller, false);

        assertThat(rejected.contactRequestStatus()).isEqualTo("REJECTED");
        assertThat(rejected.closedAt()).isNotNull();
        assertThatThrownBy(() -> service.requestContact(7L, buyer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void outsiderCannotRequestContactOrRespond() {
        Conversation conversation = conversation(user(1L, "user"), user(2L, "user"));
        conversation.setClosedAt(LocalDateTime.now());
        when(conversations.findForUpdate(7L)).thenReturn(Optional.of(conversation));
        User outsider = user(3L, "user");

        assertThatThrownBy(() -> service.requestContact(7L, outsider))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.respondToContact(7L, outsider, true))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(messages, never()).save(any());
    }

    @Test
    void buyerCanRequestContactAfterSellerClosesWithoutSelling() {
        User buyer = user(1L, "user");
        User seller = user(2L, "user");
        Conversation conversation = conversation(buyer, seller);
        Listing listing = new Listing();
        ListingStatus active = new ListingStatus();
        active.setName("active");
        listing.setStatus(active);
        conversation.setListing(listing);
        when(conversations.findForUpdate(7L)).thenReturn(Optional.of(conversation));
        when(messages.save(any(ConversationMessage.class))).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        service.close(7L, seller, false);
        var request = service.requestContact(7L, buyer);

        assertThat(request.contactRequestStatus()).isEqualTo("PENDING");
        assertThat(request.contactRequestAllowed()).isTrue();
        verify(listingService, never()).markAsSold(any(), any());
    }

    @Test
    void soldListingCannotBeReopenedThroughContactRequest() {
        User buyer = user(1L, "user");
        Conversation conversation = conversation(buyer, user(2L, "user"));
        conversation.setClosedAt(LocalDateTime.now());
        Listing listing = new Listing();
        ListingStatus sold = new ListingStatus();
        sold.setName("sold");
        listing.setStatus(sold);
        conversation.setListing(listing);
        when(conversations.findForUpdate(7L)).thenReturn(Optional.of(conversation));

        assertThat(conversation.getContactRequestStatus()).isEqualTo("NONE");
        assertThatThrownBy(() -> service.requestContact(7L, buyer))
                .isInstanceOf(IllegalArgumentException.class);
        verify(messages, never()).save(any());
    }

    private static ConversationMessage saved(ConversationMessage message) {
        message.setId(20L);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private static Conversation conversation(User buyer, User seller) {
        Conversation conversation = new Conversation();
        conversation.setId(7L);
        conversation.setBuyer(buyer);
        conversation.setSeller(seller);
        conversation.setItemName("Przedmiot");
        conversation.setUpdatedAt(LocalDateTime.now());
        return conversation;
    }

    private static User user(Long id, String roleName) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setDisplayName("Gracz" + id);
        user.setRole(new Role(roleName));
        return user;
    }
}
