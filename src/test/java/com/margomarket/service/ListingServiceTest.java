package com.margomarket.service;

import com.margomarket.dto.ListingFilter;
import com.margomarket.dto.ListingRequest;
import com.margomarket.event.ListingSoldEvent;
import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.Currency;
import com.margomarket.model.ItemType;
import com.margomarket.model.Listing;
import com.margomarket.model.ListingStatus;
import com.margomarket.model.Rarity;
import com.margomarket.model.Role;
import com.margomarket.model.Server;
import com.margomarket.model.User;
import com.margomarket.repository.CurrencyRepository;
import com.margomarket.repository.FavoriteRepository;
import com.margomarket.repository.ItemTypeRepository;
import com.margomarket.repository.ListingRepository;
import com.margomarket.repository.ListingStatusRepository;
import com.margomarket.repository.RarityRepository;
import com.margomarket.repository.ServerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private ListingStatusRepository listingStatusRepository;

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private ItemTypeRepository itemTypeRepository;

    @Mock
    private RarityRepository rarityRepository;

    @Mock
    private CurrencyRepository currencyRepository;

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ListingService listingService;

    @Test
    void createListingFillsFieldsTrimsTextSetsActiveStatusAndSaves() {
        User owner = user(1L, "user");
        ListingRequest request = request("  Smoczy miecz  ", "  Discord#1234  ");
        ListingStatus activeStatus = status("active");
        Server server = server(1L);
        ItemType itemType = itemType(2L);
        Rarity rarity = rarity(3L);
        Currency currency = currency(4L);

        mockLookups(server, itemType, rarity, currency);
        when(listingStatusRepository.findByName("active")).thenReturn(Optional.of(activeStatus));
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Listing created = listingService.createListing(request, owner);

        assertThat(created.getUser()).isSameAs(owner);
        assertThat(created.getItemName()).isEqualTo("Smoczy miecz");
        assertThat(created.getContact()).isEqualTo("Discord#1234");
        assertThat(created.getLevel()).isEqualTo(100);
        assertThat(created.getPrice()).isEqualTo(2500);
        assertThat(created.getServer()).isSameAs(server);
        assertThat(created.getItemType()).isSameAs(itemType);
        assertThat(created.getRarity()).isSameAs(rarity);
        assertThat(created.getCurrency()).isSameAs(currency);
        assertThat(created.getStatus()).isSameAs(activeStatus);
        verify(listingRepository).save(created);
    }

    @Test
    void updateListingRejectsUserWhoIsNotOwnerOrAdmin() {
        User owner = user(1L, "user");
        User stranger = user(2L, "user");
        Listing listing = listing(owner, status("active"));

        when(listingRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> listingService.updateListing(10L, request("Miecz", "kontakt"), stranger))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void updateListingRejectsSoldListingEvenForOwner() {
        User owner = user(1L, "user");
        Listing listing = listing(owner, status("sold"));

        when(listingRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> listingService.updateListing(10L, request("Miecz", "kontakt"), owner))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateListingAllowsAdminToEditSomeoneElsesActiveListing() {
        User owner = user(1L, "user");
        User admin = user(2L, "admin");
        Listing listing = listing(owner, status("active"));
        ListingRequest request = request("  Zbroja  ", "  mail@example.com  ");

        when(listingRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(listing));
        mockLookups(server(1L), itemType(2L), rarity(3L), currency(4L));

        Listing updated = listingService.updateListing(10L, request, admin);

        assertThat(updated).isSameAs(listing);
        assertThat(updated.getItemName()).isEqualTo("Zbroja");
        assertThat(updated.getContact()).isEqualTo("mail@example.com");
        assertThat(updated.getLevel()).isEqualTo(100);
        assertThat(updated.getPrice()).isEqualTo(2500);
    }

    @Test
    void markAsSoldChangesStatusAndSetsSoldAtForOwner() {
        User owner = user(1L, "user");
        Listing listing = listing(owner, status("active"));
        listing.setId(10L);
        listing.setItemName("Smoczy miecz");
        ListingStatus soldStatus = status("sold");

        when(listingRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(listing));
        when(listingStatusRepository.findByName("sold")).thenReturn(Optional.of(soldStatus));
        when(favoriteRepository.findUserIdsByListingId(10L)).thenReturn(List.of(2L, 3L));

        Listing sold = listingService.markAsSold(10L, owner);

        assertThat(sold.getStatus()).isSameAs(soldStatus);
        assertThat(sold.getSoldAt()).isNotNull();
        verify(favoriteRepository).deleteByListingId(10L);

        ArgumentCaptor<ListingSoldEvent> eventCaptor = ArgumentCaptor.forClass(ListingSoldEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().listingId()).isEqualTo(10L);
        assertThat(eventCaptor.getValue().ownerId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().observerIds()).containsExactly(2L, 3L);
        assertThat(eventCaptor.getValue().itemName()).isEqualTo("Smoczy miecz");
    }

    @Test
    void markAsSoldDoesNothingWhenListingIsAlreadySold() {
        User owner = user(1L, "user");
        Listing listing = listing(owner, status("sold"));

        when(listingRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(listing));

        Listing sold = listingService.markAsSold(10L, owner);

        assertThat(sold).isSameAs(listing);
        verify(listingStatusRepository, never()).findByName("sold");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deleteListingDeletesWhenCurrentUserIsOwner() {
        User owner = user(1L, "user");
        Listing listing = listing(owner, status("active"));
        listing.setId(10L);

        when(listingRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(listing));

        listingService.deleteListing(10L, owner);

        verify(favoriteRepository).deleteByListingId(10L);
        verify(listingRepository).delete(listing);
    }

    @Test
    void deleteListingRejectsInactiveListing() {
        User owner = user(1L, "user");
        Listing listing = listing(owner, status("sold"));

        when(listingRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(listing));

        assertThatThrownBy(() -> listingService.deleteListing(10L, owner))
                .isInstanceOf(IllegalArgumentException.class);

        verify(favoriteRepository, never()).deleteByListingId(any());
        verify(listingRepository, never()).delete(any());
    }

    @Test
    void deleteListingAllowsAdminToDeleteSoldListing() {
        User owner = user(1L, "user");
        User admin = user(2L, "admin");
        Listing listing = listing(owner, status("sold"));
        listing.setId(10L);

        when(listingRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(listing));

        listingService.deleteListing(10L, admin);

        verify(favoriteRepository).deleteByListingId(10L);
        verify(listingRepository).delete(listing);
    }

    @Test
    void getUserListingsUsesStatusSortedRepositoryQuery() {
        User owner = user(1L, "user");
        Listing active = listing(owner, status("active"));
        Listing sold = listing(owner, status("sold"));

        when(listingRepository.findByUserSortedByStatusAndCreatedAtDesc(owner)).thenReturn(List.of(active, sold));

        List<Listing> listings = listingService.getUserListings(owner);

        assertThat(listings).containsExactly(active, sold);
    }

    @Test
    void getListingThrowsWhenListingDoesNotExist() {
        when(listingRepository.findByIdWithDetails(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.getListing(10L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void searchActiveListingsUsesSearchQueryWhenSearchTextIsPresent() {
        ListingFilter filter = new ListingFilter();
        filter.setSearch("  miecz  ");
        filter.setPage(2);
        Page<Listing> expectedPage = new PageImpl<>(List.of());

        when(listingRepository.findActiveListingsFiltered(
                eq("miecz"), eq(null), eq(null), eq(null), eq(null), eq(0), eq(300), any(Pageable.class)
        )).thenReturn(expectedPage);

        Page<Listing> result = listingService.searchActiveListings(filter);

        assertThat(result).isSameAs(expectedPage);
    }

    private void mockLookups(Server server, ItemType itemType, Rarity rarity, Currency currency) {
        when(serverRepository.findById(1L)).thenReturn(Optional.of(server));
        when(itemTypeRepository.findById(2L)).thenReturn(Optional.of(itemType));
        when(rarityRepository.findById(3L)).thenReturn(Optional.of(rarity));
        when(currencyRepository.findById(4L)).thenReturn(Optional.of(currency));
    }

    private static ListingRequest request(String itemName, String contact) {
        return new ListingRequest(itemName, 2L, 100, 3L, 2500, 4L, 1L, contact);
    }

    private static User user(Long id, String roleName) {
        User user = new User();
        user.setId(id);
        user.setRole(new Role(roleName));
        return user;
    }

    private static Listing listing(User owner, ListingStatus status) {
        Listing listing = new Listing();
        listing.setUser(owner);
        listing.setStatus(status);
        return listing;
    }

    private static ListingStatus status(String name) {
        ListingStatus status = new ListingStatus();
        status.setName(name);
        return status;
    }

    private static Server server(Long id) {
        Server server = new Server();
        server.setId(id);
        server.setName("Narwhals");
        return server;
    }

    private static ItemType itemType(Long id) {
        ItemType itemType = new ItemType();
        itemType.setId(id);
        itemType.setName("Bron");
        return itemType;
    }

    private static Rarity rarity(Long id) {
        Rarity rarity = new Rarity();
        rarity.setId(id);
        rarity.setName("Unikat");
        return rarity;
    }

    private static Currency currency(Long id) {
        Currency currency = new Currency();
        currency.setId(id);
        currency.setName("PLN");
        return currency;
    }
}
