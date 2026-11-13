package com.margomarket.service;

import com.margomarket.model.User;
import com.margomarket.repository.*;
import com.margomarket.mapper.*;
import com.margomarket.exception.NotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PublicProfileServiceTest {
    UserRepository users=mock(UserRepository.class);
    ListingRepository listings=mock(ListingRepository.class);
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    PublicProfileService service=new PublicProfileService(users,listings,mock(ListingMapper.class),mock(PageMapper.class),jdbc);
    @Test void doesNotExposeUnverifiedAccount() {
        User u=new User(); u.setEmailVerified(false);
        when(users.findById(1L)).thenReturn(Optional.of(u));
        assertThatThrownBy(()->service.get(1L)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(listings,jdbc);
    }
    @Test void missingAccountIsUnavailable() {
        when(users.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.get(1L)).isInstanceOf(NotFoundException.class);
    }
    @Test void keepsSaleStatusesAndAuctionResultsSeparateFromConfirmations() {
        User u=new User(); u.setId(1L); u.setDisplayName("Gracz");
        when(users.findById(1L)).thenReturn(Optional.of(u));
        when(listings.countUserListingsByStatus(1L)).thenReturn(Collections.singletonList(new Object[]{8L,3L,5L}));
        when(jdbc.queryForObject("SELECT count(*) FROM exchange_confirmations WHERE buyer_id=? AND revoked_at IS NULL",Long.class,1L)).thenReturn(2L);
        when(jdbc.queryForObject("SELECT count(*) FROM exchange_confirmations WHERE seller_id=? AND revoked_at IS NULL",Long.class,1L)).thenReturn(1L);
        when(jdbc.queryForObject("SELECT count(*) FROM auctions a JOIN listings l ON l.id=a.id WHERE l.user_id=? AND a.status IN ('ENDED_WITH_WINNER','ENDED_NO_BIDS')",Long.class,1L)).thenReturn(9L);
        var result=service.get(1L);
        assertThat(result.activeListings()).isEqualTo(3); assertThat(result.soldListings()).isEqualTo(5);
        assertThat(result.confirmedPurchases()).isEqualTo(2); assertThat(result.confirmedSales()).isEqualTo(1);
        assertThat(result.endedAuctions()).isEqualTo(9);
    }
}
