package com.margomarket.repository;

import com.margomarket.model.ConversationMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    Page<ConversationMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    Optional<ConversationMessage> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    @Query(value = """
        SELECT COUNT(*) FROM conversation_messages
        WHERE conversation_id = :conversationId AND id > :lastReadId
          AND (sender_id IS NULL OR sender_id <> :userId)
        """, nativeQuery = true)
    long countUnread(@Param("conversationId") Long conversationId,
                     @Param("lastReadId") Long lastReadId,
                     @Param("userId") Long userId);
}
