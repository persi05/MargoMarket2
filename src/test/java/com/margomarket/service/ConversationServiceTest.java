package com.margomarket.service;

import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.model.Conversation;
import com.margomarket.model.ConversationMessage;
import com.margomarket.model.Listing;
import com.margomarket.model.Role;
import com.margomarket.model.User;
import com.margomarket.repository.ConversationMessageRepository;
import com.margomarket.repository.ConversationRepository;
import com.margomarket.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {
    @Mock ConversationRepository conversations;
    @Mock ConversationMessageRepository messages;
    @Mock ListingRepository listings;
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
        assertThat(conversation.getBuyerLastReadId()).isEqualTo(20L);
        assertThatThrownBy(() -> service.requestIntermediary(7L, buyer))
                .isInstanceOf(IllegalArgumentException.class);
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
        user.setRole(new Role(roleName));
        return user;
    }
}
