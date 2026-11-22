package com.margomarket.profile.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.margomarket.listing.ListingQueries;
import com.margomarket.listing.UserListingStats;
import com.margomarket.listing.UserStats;
import com.margomarket.shared.NotFoundException;
import com.margomarket.user.User;
import com.margomarket.user.UserDirectory;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PublicProfileServiceTest {
    UserDirectory users = mock(UserDirectory.class);
    ListingQueries listings = mock(ListingQueries.class);
    UserListingStats stats = mock(UserListingStats.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    PublicProfileService service = new PublicProfileService(users, listings, stats, jdbc);

    @Test
    void doesNotExposeUnverifiedAccount() {
        when(users.findVerified(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(listings, stats, jdbc);
    }

    @Test
    void missingAccountIsUnavailable() {
        when(users.findVerified(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void keepsSaleStatusesAndAuctionResultsSeparateFromConfirmations() {
        User u = new User();
        u.setId(1L);
        u.setDisplayName("Gracz");
        when(users.findVerified(1L)).thenReturn(Optional.of(u));
        when(stats.forUser(1L)).thenReturn(new UserStats(8L, 3L, 5L));
        when(jdbc.queryForObject(
                        "SELECT count(*) FROM exchange_confirmations WHERE buyer_id=? AND revoked_at IS NULL",
                        Long.class,
                        1L))
                .thenReturn(2L);
        when(jdbc.queryForObject(
                        "SELECT count(*) FROM exchange_confirmations WHERE seller_id=? AND revoked_at IS NULL",
                        Long.class,
                        1L))
                .thenReturn(1L);
        when(jdbc.queryForObject(
                        "SELECT count(*) FROM auctions a JOIN listings l ON l.id=a.id WHERE l.user_id=? AND a.status IN ('ENDED_WITH_WINNER','ENDED_NO_BIDS')",
                        Long.class,
                        1L))
                .thenReturn(9L);
        var result = service.get(1L);
        assertThat(result.activeListings()).isEqualTo(3);
        assertThat(result.soldListings()).isEqualTo(5);
        assertThat(result.confirmedPurchases()).isEqualTo(2);
        assertThat(result.confirmedSales()).isEqualTo(1);
        assertThat(result.endedAuctions()).isEqualTo(9);
    }
}
