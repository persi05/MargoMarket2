package com.margomarket.service;

import com.margomarket.dto.ChatMessageResponse;
import com.margomarket.dto.ConversationReportResponse;
import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.exception.NotFoundException;
import com.margomarket.mapper.ConversationMessageMapper;
import com.margomarket.model.Conversation;
import com.margomarket.model.ConversationReport;
import com.margomarket.model.User;
import com.margomarket.repository.ConversationMessageRepository;
import com.margomarket.repository.ConversationReportRepository;
import com.margomarket.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationReportService {
    private static final int REPORT_MESSAGE_LIMIT = 50;

    private final ConversationRepository conversations;
    private final ConversationReportRepository reports;
    private final ConversationMessageRepository messages;
    private final TextModerationService moderation;

    @Transactional
    public ConversationReportResponse report(Long id, String reason, String details, User reporter) {
        Conversation conversation = conversations.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Rozmowa nie istnieje"));
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
        return toResponse(reports.save(report));
    }

    public List<ConversationReportResponse> reports(User admin) {
        requireAdmin(admin);
        return reports.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
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
        return toResponse(report);
    }

    public List<ChatMessageResponse> reportMessages(Long id, User admin) {
        requireAdmin(admin);
        ConversationReport report = reports.findById(id)
                .orElseThrow(() -> new NotFoundException("Zgłoszenie nie istnieje"));
        Conversation conversation = conversations.findDetailedById(report.getConversation().getId())
                .orElseThrow(() -> new NotFoundException("Rozmowa nie istnieje"));
        return messages.findByConversationIdOrderByIdDesc(conversation.getId(), PageRequest.of(0, REPORT_MESSAGE_LIMIT))
                .getContent().stream().map(message -> ConversationMessageMapper.toResponse(message, conversation)).toList();
    }

    private void requireAdmin(User user) {
        if (!user.isAdmin()) {
            throw new ForbiddenOperationException("Tylko administrator może przeglądać zgłoszenia.");
        }
    }

    private ConversationReportResponse toResponse(ConversationReport report) {
        Conversation conversation = report.getConversation();
        String reportedUserDisplayName = report.getReporter().getId().equals(conversation.getBuyer().getId())
                ? conversation.getSeller().getDisplayName() : conversation.getBuyer().getDisplayName();
        return new ConversationReportResponse(report.getId(), conversation.getId(), conversation.getItemName(),
                conversation.getBuyer().getDisplayName(), conversation.getSeller().getDisplayName(),
                report.getReporter().getDisplayName(), reportedUserDisplayName, report.getReason(), report.getDetails(),
                report.getStatus(), report.getCreatedAt(), report.getReviewedAt());
    }

}
