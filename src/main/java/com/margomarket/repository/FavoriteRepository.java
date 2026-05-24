package com.margomarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.margomarket.model.Listing;
import com.margomarket.model.ListingFavorite;
import com.margomarket.model.User;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<ListingFavorite, Long> {

    boolean existsByUserAndListing(User user, Listing listing);

    Optional<ListingFavorite> findByUserAndListing(User user, Listing listing);

    @Query("""
        SELECT f FROM ListingFavorite f
        JOIN FETCH f.listing l
        JOIN FETCH l.user
        JOIN FETCH l.itemType
        JOIN FETCH l.rarity
        JOIN FETCH l.currency
        JOIN FETCH l.server
        JOIN FETCH l.status
        WHERE f.user = :user
          AND l.status.name = 'active'
        ORDER BY f.createdAt DESC
        """)
    List<ListingFavorite> findByUserWithDetails(@Param("user") User user);

    @Query("""
        SELECT f.listing.id FROM ListingFavorite f
        WHERE f.user = :user
          AND f.listing.status.name = 'active'
        """)
    List<Long> findFavoriteIdsByUser(@Param("user") User user);

    @Query("""
        SELECT f.user.id FROM ListingFavorite f
        WHERE f.listing.id = :listingId
        """)
    List<Long> findUserIdsByListingId(@Param("listingId") Long listingId);

    void deleteByUserAndListing(User user, Listing listing);

    @Modifying
    @Query("DELETE FROM ListingFavorite f WHERE f.listing.id = :listingId")
    int deleteByListingId(@Param("listingId") Long listingId);
}
