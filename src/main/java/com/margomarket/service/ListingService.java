
package com.margomarket.service;

import com.margomarket.dto.ListingFilter;
import com.margomarket.dto.ListingRequest;
import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.Currency;
import com.margomarket.model.ItemType;
import com.margomarket.model.Listing;
import com.margomarket.model.ListingStatus;
import com.margomarket.model.Rarity;
import com.margomarket.model.Server;
import com.margomarket.model.User;
import com.margomarket.repository.CurrencyRepository;
import com.margomarket.repository.FavoriteRepository;
import com.margomarket.repository.ItemTypeRepository;
import com.margomarket.repository.ListingRepository;
import com.margomarket.repository.ListingStatusRepository;
import com.margomarket.repository.RarityRepository;
import com.margomarket.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.margomarket.event.ListingSoldEvent;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingService {

    private static final int PAGE_SIZE = 50;

    private final ListingRepository listingRepository;
    private final ListingStatusRepository listingStatusRepository;
    private final ServerRepository serverRepository;
    private final ItemTypeRepository itemTypeRepository;
    private final RarityRepository rarityRepository;
    private final CurrencyRepository currencyRepository;
    private final FavoriteRepository favoriteRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Page<Listing> searchActiveListings(ListingFilter filter) {
        Pageable pageable = PageRequest.of(Math.max(0, filter.getPage() - 1), PAGE_SIZE);

        if (!filter.hasSearch()) {
            return listingRepository.findActiveListingsFilteredWithoutSearch(
                    filter.getServerId(),
                    filter.getItemTypeId(),
                    filter.getRarityId(),
                    filter.getCurrencyId(),
                    filter.getMinLevelOrDefault(),
                    filter.getMaxLevelOrDefault(),
                    pageable
            );
        }

        return listingRepository.findActiveListingsFiltered(
                filter.getSearchOrEmpty(),
                filter.getServerId(),
                filter.getItemTypeId(),
                filter.getRarityId(),
                filter.getCurrencyId(),
                filter.getMinLevelOrDefault(),
                filter.getMaxLevelOrDefault(),
                pageable
        );
    }

    public Page<Listing> searchAllListingsAdmin(ListingFilter filter) {
        Pageable pageable = PageRequest.of(Math.max(0, filter.getPage() - 1), PAGE_SIZE);
        String statusName = filter.getStatus() == null || filter.getStatus().isBlank() ? null : filter.getStatus();

        if (!filter.hasSearch()) {
            return listingRepository.findAllListingsAdminWithoutSearch(
                    filter.getServerId(),
                    statusName,
                    pageable
            );
        }

        return listingRepository.findAllListingsAdmin(
                filter.getSearchOrEmpty(),
                filter.getServerId(),
                statusName,
                pageable
        );
    }

    public Listing getListing(Long id) {
        return listingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Ogłoszenie nie istnieje"));
    }

    public List<Listing> getUserListings(User user) {
        return listingRepository.findByUserSortedByStatusAndCreatedAtDesc(user);
    }

    @Transactional
    public Listing createListing(ListingRequest request, User owner) {
        Listing listing = new Listing();
        listing.setUser(owner);
        applyRequest(listing, request);
        listing.setStatus(getStatus("active"));
        return listingRepository.save(listing);
    }

    @Transactional
    public Listing updateListing(Long id, ListingRequest request, User currentUser) {
        Listing listing = getListing(id);
        requireOwnerOrAdmin(listing, currentUser);
        if (listing.isSold()) {
            throw new IllegalArgumentException("Nie można edytować sprzedanego ogłoszenia");
        }
        applyRequest(listing, request);
        return listing;
    }

    @Transactional
    public Listing markAsSold(Long id, User currentUser) {
        Listing listing = getListing(id);
        requireOwnerOrAdmin(listing, currentUser);
        if (listing.isSold()) {
            return listing;
        }
        listing.setStatus(getStatus("sold"));
        listing.setSoldAt(LocalDateTime.now());
        List<Long> observerIds = favoriteRepository.findUserIdsByListingId(listing.getId());
        favoriteRepository.deleteByListingId(listing.getId());
        eventPublisher.publishEvent(new ListingSoldEvent(
                listing.getId(),
                listing.getUser().getId(),
                observerIds,
                listing.getItemName()
        ));
        return listing;
    }

    @Transactional
    public void deleteListing(Long id, User currentUser) {
        Listing listing = getListing(id);
        requireOwnerOrAdmin(listing, currentUser);
        if (!currentUser.isAdmin() && !listing.isActive()) {
            throw new IllegalArgumentException("Można usunąć tylko aktywne ogłoszenie");
        }
        favoriteRepository.deleteByListingId(listing.getId());
        listingRepository.delete(listing);
    }

    private void applyRequest(Listing listing, ListingRequest request) {
        listing.setItemName(request.itemName().trim());
        listing.setItemType(getItemType(request.itemTypeId()));
        listing.setLevel(request.level());
        listing.setRarity(getRarity(request.rarityId()));
        listing.setPrice(request.price());
        listing.setCurrency(getCurrency(request.currencyId()));
        listing.setServer(getServer(request.serverId()));
        listing.setContact(request.contact().trim());
    }

    private void requireOwnerOrAdmin(Listing listing, User user) {
        if (!listing.getUser().getId().equals(user.getId()) && !user.isAdmin()) {
            throw new ForbiddenOperationException("Brak uprawnień do tego ogłoszenia");
        }
    }

    private ListingStatus getStatus(String name) {
        return listingStatusRepository.findByName(name)
                .orElseThrow(() -> new NotFoundException("Brakuje statusu: " + name));
    }

    private Server getServer(Long id) {
        return serverRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Serwer nie istnieje"));
    }

    private ItemType getItemType(Long id) {
        return itemTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Typ przedmiotu nie istnieje"));
    }

    private Rarity getRarity(Long id) {
        return rarityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Rzadkość nie istnieje"));
    }

    private Currency getCurrency(Long id) {
        return currencyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Waluta nie istnieje"));
    }
}
