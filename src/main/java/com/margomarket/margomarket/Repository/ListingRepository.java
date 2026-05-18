package com.margomarket.margomarket.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.margomarket.margomarket.model.Listing;
import com.margomarket.margomarket.model.User;

import java.util.List;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    List<Listing> findByUserOrderByCreatedAtDesc(User user);

    @Query("""
        SELECT l FROM Listing l
        JOIN FETCH l.user
        JOIN FETCH l.itemType
        JOIN FETCH l.rarity
        JOIN FETCH l.currency
        JOIN FETCH l.server
        JOIN FETCH l.status
        WHERE l.status.name = 'active'
          AND (:search IS NULL OR LOWER(l.itemName) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:serverId IS NULL OR l.server.id = :serverId)
          AND (:itemTypeId IS NULL OR l.itemType.id = :itemTypeId)
          AND (:rarityId IS NULL OR l.rarity.id = :rarityId)
          AND (:currencyId IS NULL OR l.currency.id = :currencyId)
          AND l.level BETWEEN :minLevel AND :maxLevel
        ORDER BY l.createdAt DESC
        """)
    Page<Listing> findActiveListingsFiltered(
            @Param("search") String search,
            @Param("serverId") Long serverId,
            @Param("itemTypeId") Long itemTypeId,
            @Param("rarityId") Long rarityId,
            @Param("currencyId") Long currencyId,
            @Param("minLevel") int minLevel,
            @Param("maxLevel") int maxLevel,
            Pageable pageable
    );

    @Query("""
        SELECT l FROM Listing l
        JOIN FETCH l.user
        JOIN FETCH l.itemType
        JOIN FETCH l.rarity
        JOIN FETCH l.currency
        JOIN FETCH l.server
        JOIN FETCH l.status
        WHERE (:search IS NULL OR LOWER(l.itemName) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:serverId IS NULL OR l.server.id = :serverId)
          AND (:statusName IS NULL OR l.status.name = :statusName)
        ORDER BY l.createdAt DESC
        """)
    Page<Listing> findAllListingsAdmin(
            @Param("search") String search,
            @Param("serverId") Long serverId,
            @Param("statusName") String statusName,
            Pageable pageable
    );

    @Query(value = "SELECT mark_listing_as_sold(:listingId, :userId)", nativeQuery = true)
    Boolean markAsSoldDb(@Param("listingId") Long listingId, @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Listing l WHERE l.id = :id AND l.user.id = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
        SELECT COUNT(l) FROM Listing l
        WHERE l.status.name = 'active'
          AND (:search IS NULL OR LOWER(l.itemName) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:serverId IS NULL OR l.server.id = :serverId)
          AND (:itemTypeId IS NULL OR l.itemType.id = :itemTypeId)
          AND (:rarityId IS NULL OR l.rarity.id = :rarityId)
          AND (:currencyId IS NULL OR l.currency.id = :currencyId)
          AND l.level BETWEEN :minLevel AND :maxLevel
        """)
    long countActiveFiltered(
            @Param("search") String search,
            @Param("serverId") Long serverId,
            @Param("itemTypeId") Long itemTypeId,
            @Param("rarityId") Long rarityId,
            @Param("currencyId") Long currencyId,
            @Param("minLevel") int minLevel,
            @Param("maxLevel") int maxLevel
    );
}