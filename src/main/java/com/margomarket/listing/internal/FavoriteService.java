package com.margomarket.listing.internal;

import com.margomarket.listing.Listing;
import com.margomarket.shared.NotFoundException;
import com.margomarket.user.User;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
