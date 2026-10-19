package com.margomarket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    public Page<Listing> getUserFavoriteListings(User user, int page) {
        return favoriteRepository.findByUserWithDetails(user,
                PageRequest.of(Math.max(0, page - 1), 20)).map(ListingFavorite::getListing);
    }

    public List<Long> getUserFavoriteIds(User user, List<Long> listingIds) {
        if (listingIds.size() > 100) {
            throw new IllegalArgumentException("Można sprawdzić maksymalnie 100 ogłoszeń naraz");
        }
        if (listingIds.isEmpty()) return List.of();
        return favoriteRepository.findFavoriteIdsByUser(user, listingIds);
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
