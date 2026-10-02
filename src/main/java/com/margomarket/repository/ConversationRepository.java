package com.margomarket.repository;

import com.margomarket.model.Conversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    @EntityGraph(attributePaths = {"buyer", "seller", "intermediary", "listing"})
    @Query("SELECT c FROM Conversation c WHERE c.id = :id")
    Optional<Conversation> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversation c WHERE c.id = :id")
    Optional<Conversation> findForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"buyer", "seller", "intermediary", "listing"})
    @Query("SELECT c FROM Conversation c WHERE c.listing.id = :listingId AND c.buyer.id = :buyerId")
    Optional<Conversation> findByListingAndBuyer(@Param("listingId") Long listingId, @Param("buyerId") Long buyerId);

    @EntityGraph(attributePaths = {"buyer", "seller", "intermediary", "listing"})
    @Query("""
        SELECT c FROM Conversation c
        WHERE c.buyer.id = :userId OR c.seller.id = :userId OR c.intermediary.id = :userId
        ORDER BY c.updatedAt DESC, c.id DESC
        """)
    List<Conversation> findVisibleTo(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"buyer", "seller", "intermediary", "listing"})
    @Query("SELECT c FROM Conversation c WHERE c.intermediaryStatus = 'REQUESTED' ORDER BY c.updatedAt DESC")
    List<Conversation> findIntermediaryRequests();
}
