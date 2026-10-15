package com.margomarket.repository;

import com.margomarket.model.ConversationReport;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationReportRepository extends JpaRepository<ConversationReport, Long> {
    boolean existsByConversationIdAndReporterId(Long conversationId, Long reporterId);

    @EntityGraph(attributePaths = {"conversation", "conversation.buyer", "conversation.seller", "reporter"})
    List<ConversationReport> findAllByOrderByCreatedAtDesc();
}
