package com.margomarket.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.margomarket.dto.*;
import com.margomarket.exception.*;
import com.margomarket.model.*;
import com.margomarket.repository.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GoldListingServiceTest {

    ListingRepository listings = mock(ListingRepository.class);
    ServerRepository servers = mock(ServerRepository.class);
    CurrencyRepository currencies = mock(CurrencyRepository.class);
    ListingStatusRepository statuses = mock(ListingStatusRepository.class);
    FavoriteRepository favorites = mock(FavoriteRepository.class);
    GoldListingService service = new GoldListingService(
        listings,
        servers,
        currencies,
        statuses,
        favorites
    );

    @Test
    void creationDisabledByDefault() {
        assertThatThrownBy(() ->
            service.create(
                new GoldListingRequest(
                    1L,
                    1500000000L,
                    "PLN_PER_G",
                    1000000000L,
                    2300L,
                    null,
                    null
                ),
                new User()
            )
        ).isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(listings);
    }

    @Test
    void createsGoldWithoutCatalogItem() {
        ReflectionTestUtils.setField(service, "enabled", true);
        User owner = new User();
        owner.setId(1L);
        when(servers.findById(2L)).thenReturn(Optional.of(new Server()));
        when(currencies.findByNameIgnoreCase("PLN")).thenReturn(Optional.of(new Currency()));
        ListingStatus status = new ListingStatus();
        status.setName("active");
        when(statuses.findByName("active")).thenReturn(Optional.of(status));
        when(listings.save(any())).thenAnswer(i -> i.getArgument(0));
        Listing result = service.create(
            new GoldListingRequest(2L, 1500000000L, "PLN_PER_G", 1000000000L, 2300L, "Opis", null),
            owner
        );
        assertThat(result.getListingKind()).isEqualTo("GOLD");
        assertThat(result.getGoldAmount()).isEqualTo(1500000000L);
        assertThat(result.getItem()).isNull();
        assertThat(result.getItemType()).isNull();
        assertThat(result.getLevel()).isNull();
        assertThat(result.getPrice()).isNull();
        assertThat(result.getPriceMinor()).isEqualTo(3450);
    }

    @Test
    void refusesEditingOtherUsersGold() {
        User owner = new User();
        owner.setId(1L);
        User other = new User();
        other.setId(2L);
        Role role = new Role();
        role.setName("user");
        other.setRole(role);
        Listing l = new Listing();
        l.setUser(owner);
        l.setListingKind("GOLD");
        when(listings.findByIdWithDetails(3L)).thenReturn(Optional.of(l));
        assertThatThrownBy(() ->
            service.update(
                3L,
                new GoldListingRequest(1L, 1L, "M_PER_PLN", 1L, 100L, null, null),
                other
            )
        ).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void rejectsUnknownSorting() {
        GoldListingFilter f = new GoldListingFilter();
        f.setSort("unsafe");
        assertThatThrownBy(() -> service.search(f)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(listings);
    }
}
