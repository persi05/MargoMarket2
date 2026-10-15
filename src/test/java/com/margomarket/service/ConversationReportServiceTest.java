package com.margomarket.service;

import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.model.Conversation;
import com.margomarket.model.ConversationMessage;
import com.margomarket.model.ConversationReport;
import com.margomarket.model.Role;
import com.margomarket.model.User;
import com.margomarket.repository.ConversationMessageRepository;
import com.margomarket.repository.ConversationReportRepository;
import com.margomarket.repository.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationReportServiceTest {
    @Mock ConversationRepository conversations;
    @Mock ConversationReportRepository reports;
    @Mock ConversationMessageRepository messages;
    @Mock TextModerationService moderation;
    @InjectMocks ConversationReportService service;

    @Test
    void outsiderCannotReportPrivateConversation() {
        when(conversations.findDetailedById(7L)).thenReturn(Optional.of(conversation()));

        assertThatThrownBy(() -> service.report(7L, "SCAM", "", user(3L, "user")))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(reports, never()).save(any());
    }

    @Test
    void duplicateReportIsRejected() {
        when(conversations.findDetailedById(7L)).thenReturn(Optional.of(conversation()));
        when(reports.existsByConversationIdAndReporterId(7L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.report(7L, "SCAM", "", user(1L, "user")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(reports, never()).save(any());
    }

    @Test
    void reportIdentifiesOtherConversationParticipant() {
        when(conversations.findDetailedById(7L)).thenReturn(Optional.of(conversation()));
        when(moderation.masker()).thenReturn(text -> text.replace("sytuacji", "********"));
        when(reports.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var report = service.report(7L, "FRAUD", "Opis sytuacji", user(1L, "user"));

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
    void adminReadsRecentMessagesFromReportedConversation() {
        Conversation conversation = conversation();
        ConversationReport report = new ConversationReport();
        report.setConversation(conversation);
        ConversationMessage message = new ConversationMessage();
        message.setId(20L);
        message.setSender(conversation.getSeller());
        message.setBody("Wiadomość");
        when(reports.findById(5L)).thenReturn(Optional.of(report));
        when(conversations.findDetailedById(7L)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdOrderByIdDesc(7L, PageRequest.of(0, 50)))
                .thenReturn(new PageImpl<>(List.of(message)));

        var result = service.reportMessages(5L, user(3L, "admin"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).senderRole()).isEqualTo("seller");
        assertThat(result.get(0).body()).isEqualTo("Wiadomość");
    }

    private static Conversation conversation() {
        Conversation conversation = new Conversation();
        conversation.setId(7L);
        conversation.setBuyer(user(1L, "user"));
        conversation.setSeller(user(2L, "user"));
        conversation.setItemName("Przedmiot");
        return conversation;
    }

    private static User user(Long id, String roleName) {
        User user = new User();
        user.setId(id);
        user.setDisplayName("Gracz" + id);
        user.setRole(new Role(roleName));
        return user;
    }
}
