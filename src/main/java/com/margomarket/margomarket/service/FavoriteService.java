package com.margomarket.margomarket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.margomarket.margomarket.model.Listing;
import com.margomarket.margomarket.model.ListingFavorite;
import com.margomarket.margomarket.model.User;
import com.margomarket.margomarket.repository.FavoriteRepository;
import com.margomarket.margomarket.repository.ListingRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ListingRepository listingRepository;

    public List<Listing> getUserFavoriteListings(User user) {
        return favoriteRepository.findByUserWithDetails(user)
                .stream()
                .map(ListingFavorite::getListing)
                .toList();
    }

    public List<Long> getUserFavoriteIds(User user) {
        return favoriteRepository.findFavoriteIdsByUser(user);
    }

    public boolean isFavorite(User user, Listing listing) {
        return favoriteRepository.existsByUserAndListing(user, listing);
    }

    @Transactional
    public boolean toggleFavorite(User user, Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Listing not found: " + listingId));

        if (favoriteRepository.existsByUserAndListing(user, listing)) {
            favoriteRepository.deleteByUserAndListing(user, listing);
            return false;
        } else {
            favoriteRepository.save(new ListingFavorite(user, listing));
            return true;
        }
    }

    @Transactional
    public void removeFavorite(User user, Long listingId) {
        listingRepository.findById(listingId).ifPresent(listing ->
                favoriteRepository.deleteByUserAndListing(user, listing)
        );
    }
}