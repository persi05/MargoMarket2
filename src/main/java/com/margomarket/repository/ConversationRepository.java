package com.margomarket.repository;

import com.margomarket.model.Conversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    @EntityGraph(attributePaths = {"buyer", "seller", "intermediary", "listing", "listing.status"})
    @Query("SELECT c FROM Conversation c WHERE c.id = :id")
    Optional<Conversation> findDetailedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversation c WHERE c.id = :id")
    Optional<Conversation> findForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"buyer", "seller", "intermediary", "listing", "listing.status"})
    @Query("SELECT c FROM Conversation c WHERE c.listing.id = :listingId AND c.buyer.id = :buyerId")
    Optional<Conversation> findByListingAndBuyer(@Param("listingId") Long listingId, @Param("buyerId") Long buyerId);

    @EntityGraph(attributePaths = {"buyer", "seller", "intermediary", "listing", "listing.status"})
    @Query("""
        SELECT c FROM Conversation c
        WHERE c.buyer.id = :userId OR c.seller.id = :userId OR c.intermediary.id = :userId
        ORDER BY c.updatedAt DESC, c.id DESC
        """)
    List<Conversation> findVisibleTo(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"buyer", "seller", "intermediary", "listing", "listing.status"})
    @Query(value = """
        SELECT c FROM Conversation c
        WHERE (c.buyer.id = :userId OR c.seller.id = :userId OR c.intermediary.id = :userId)
          AND (:scope = 'ALL'
               OR (:scope = 'EXCHANGES' AND c.intermediary.id = :userId)
               OR (:scope = 'LISTINGS' AND (c.intermediary IS NULL OR c.intermediary.id <> :userId)))
        ORDER BY c.updatedAt DESC, c.id DESC
        """, countQuery = """
        SELECT COUNT(c) FROM Conversation c
        WHERE (c.buyer.id = :userId OR c.seller.id = :userId OR c.intermediary.id = :userId)
          AND (:scope = 'ALL'
               OR (:scope = 'EXCHANGES' AND c.intermediary.id = :userId)
               OR (:scope = 'LISTINGS' AND (c.intermediary IS NULL OR c.intermediary.id <> :userId)))
        """)
    Page<Conversation> findVisiblePage(@Param("userId") Long userId,
                                       @Param("scope") String scope, Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "seller", "intermediary", "listing", "listing.status"})
    @Query("SELECT c FROM Conversation c ORDER BY c.updatedAt DESC, c.id DESC")
    List<Conversation> findAllDetailedOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = {"buyer", "seller", "intermediary", "listing", "listing.status"})
    @Query(value = "SELECT c FROM Conversation c ORDER BY c.updatedAt DESC, c.id DESC",
            countQuery = "SELECT COUNT(c) FROM Conversation c")
    Page<Conversation> findAllDetailedPage(Pageable pageable);

    @EntityGraph(attributePaths = {"buyer", "seller", "intermediary", "listing", "listing.status"})
    @Query("SELECT c FROM Conversation c WHERE c.intermediaryStatus = 'REQUESTED' AND c.closedAt IS NULL ORDER BY c.updatedAt ASC, c.id ASC")
    List<Conversation> findIntermediaryRequests();

    @EntityGraph(attributePaths = {"buyer", "seller", "intermediary", "listing", "listing.status"})
    @Query("""
        SELECT c FROM Conversation c
        WHERE (c.intermediaryStatus = 'REQUESTED' AND c.closedAt IS NULL)
           OR c.intermediary.id = :adminId
        ORDER BY c.updatedAt DESC, c.id DESC
        """)
    List<Conversation> findIntermediaryExchanges(@Param("adminId") Long adminId);
}
