package com.margomarket.service;

import com.margomarket.exception.NotFoundException;
import com.margomarket.model.Listing;
import com.margomarket.model.ListingFavorite;
import com.margomarket.model.ListingStatus;
import com.margomarket.model.User;
import com.margomarket.repository.FavoriteRepository;
import com.margomarket.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private FavoriteService favoriteService;

    @Test
    void getUserFavoriteListingsReturnsListingsFromFavoriteRows() {
        User user = new User();
        Listing first = listing("active");
        Listing second = listing("active");

        when(favoriteRepository.findByUserWithDetails(user))
                .thenReturn(List.of(new ListingFavorite(user, first), new ListingFavorite(user, second)));

        List<Listing> favorites = favoriteService.getUserFavoriteListings(user);

        assertThat(favorites).containsExactly(first, second);
    }

    @Test
    void addFavoriteSavesFavoriteForActiveListingWhenItDoesNotExistYet() {
        User user = new User();
        Listing listing = listing("active");

        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(favoriteRepository.existsByUserAndListing(user, listing)).thenReturn(false);

        favoriteService.addFavorite(user, 10L);

        verify(favoriteRepository).save(any(ListingFavorite.class));
    }

    @Test
    void addFavoriteDoesNotSaveDuplicateFavorite() {
        User user = new User();
        Listing listing = listing("active");

        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(favoriteRepository.existsByUserAndListing(user, listing)).thenReturn(true);

        favoriteService.addFavorite(user, 10L);

        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void addFavoriteRejectsInactiveListing() {
        User user = new User();
        Listing listing = listing("sold");

        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> favoriteService.addFavorite(user, 10L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void addFavoriteThrowsWhenListingDoesNotExist() {
        when(listingRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.addFavorite(new User(), 10L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeFavoriteDeletesOnlyWhenListingExists() {
        User user = new User();
        Listing listing = listing("active");

        when(listingRepository.findById(10L)).thenReturn(Optional.of(listing));

        favoriteService.removeFavorite(user, 10L);

        verify(favoriteRepository).deleteByUserAndListing(user, listing);
    }

    @Test
    void removeFavoriteDoesNothingWhenListingDoesNotExist() {
        User user = new User();
        when(listingRepository.findById(10L)).thenReturn(Optional.empty());

        favoriteService.removeFavorite(user, 10L);

        verify(favoriteRepository, never()).deleteByUserAndListing(any(), any());
    }

    private static Listing listing(String statusName) {
        ListingStatus status = new ListingStatus();
        status.setName(statusName);

        Listing listing = new Listing();
        listing.setStatus(status);
        return listing;
    }
}
