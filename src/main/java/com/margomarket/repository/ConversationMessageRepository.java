package com.margomarket.repository;

import com.margomarket.model.ConversationMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {
    @EntityGraph(attributePaths = {"sender"})
    Page<ConversationMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    Optional<ConversationMessage> findFirstByConversationIdOrderByIdDesc(Long conversationId);

    @Query(value = """
        SELECT DISTINCT ON (conversation_id) conversation_id, body
        FROM conversation_messages
        WHERE conversation_id IN (:conversationIds)
        ORDER BY conversation_id, id DESC
        """, nativeQuery = true)
    List<Object[]> findLatestBodies(@Param("conversationIds") List<Long> conversationIds);

    @Query(value = """
        SELECT c.id, COUNT(m.id)
        FROM conversations c
        JOIN conversation_messages m ON m.conversation_id = c.id
        WHERE c.id IN (:conversationIds)
          AND (c.buyer_id = :userId OR c.seller_id = :userId OR c.intermediary_id = :userId)
          AND m.id > CASE
              WHEN c.buyer_id = :userId THEN c.buyer_last_read_id
              WHEN c.seller_id = :userId THEN c.seller_last_read_id
              ELSE c.intermediary_last_read_id
          END
          AND (m.sender_id IS NULL OR m.sender_id <> :userId)
        GROUP BY c.id
        """, nativeQuery = true)
    List<Object[]> countUnreadForConversations(@Param("conversationIds") List<Long> conversationIds,
                                                @Param("userId") Long userId);

    @Query(value = """
        SELECT COUNT(*) FROM conversation_messages
        WHERE conversation_id = :conversationId AND id > :lastReadId
          AND (sender_id IS NULL OR sender_id <> :userId)
        """, nativeQuery = true)
    long countUnread(@Param("conversationId") Long conversationId,
                     @Param("lastReadId") Long lastReadId,
                     @Param("userId") Long userId);

    @Query(value = """
        SELECT COUNT(*)
        FROM conversations c
        JOIN conversation_messages m ON m.conversation_id = c.id
        WHERE (c.buyer_id = :userId OR c.seller_id = :userId OR c.intermediary_id = :userId)
          AND m.id > CASE
              WHEN c.buyer_id = :userId THEN c.buyer_last_read_id
              WHEN c.seller_id = :userId THEN c.seller_last_read_id
              ELSE c.intermediary_last_read_id
          END
          AND (m.sender_id IS NULL OR m.sender_id <> :userId)
        """, nativeQuery = true)
    long countUnreadForUser(@Param("userId") Long userId);
}
