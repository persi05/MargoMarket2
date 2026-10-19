package com.margomarket.repository;

import com.margomarket.model.ConversationReport;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ConversationReportRepository extends JpaRepository<ConversationReport, Long> {
    boolean existsByConversationIdAndReporterId(Long conversationId, Long reporterId);

    @EntityGraph(attributePaths = {"conversation", "conversation.buyer", "conversation.seller", "reporter"})
    List<ConversationReport> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"conversation", "conversation.buyer", "conversation.seller", "reporter"})
    Page<ConversationReport> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
