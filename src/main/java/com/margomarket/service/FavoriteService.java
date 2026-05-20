package com.margomarket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.Listing;
import com.margomarket.model.ListingFavorite;
import com.margomarket.model.User;
import com.margomarket.repository.FavoriteRepository;
import com.margomarket.repository.ListingRepository;

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
    public void addFavorite(User user, Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Ogłoszenie nie istnieje"));

        if (!listing.isActive()) {
            throw new IllegalArgumentException("Do ulubionych można dodać tylko aktywne ogłoszenie");
        }

        if (!favoriteRepository.existsByUserAndListing(user, listing)) {
            favoriteRepository.save(new ListingFavorite(user, listing));
        }
    }

    @Transactional
    public void removeFavorite(User user, Long listingId) {
        listingRepository.findById(listingId).ifPresent(listing ->
                favoriteRepository.deleteByUserAndListing(user, listing)
        );
    }
}
