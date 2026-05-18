package com.margomarket.margomarket.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.margomarket.margomarket.model.Listing;
import com.margomarket.margomarket.model.ListingFavorite;
import com.margomarket.margomarket.model.User;

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
        ORDER BY f.createdAt DESC
        """)
    List<ListingFavorite> findByUserWithDetails(@Param("user") User user);

    @Query("SELECT f.listing.id FROM ListingFavorite f WHERE f.user = :user")
    List<Long> findFavoriteIdsByUser(@Param("user") User user);

    void deleteByUserAndListing(User user, Listing listing);
}