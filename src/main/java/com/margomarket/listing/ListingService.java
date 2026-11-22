
package com.margomarket.listing;

import com.margomarket.listing.internal.FavoriteRepository;
import com.margomarket.listing.internal.ListingRepository;
import com.margomarket.listing.internal.ListingRequest;
import com.margomarket.listing.internal.ListingStatusRepository;
import com.margomarket.listing.internal.PrivateListingDetails;
import com.margomarket.catalog.Currency;
import com.margomarket.catalog.Catalog;
import com.margomarket.catalog.Item;
import com.margomarket.catalog.ItemRules;
import com.margomarket.catalog.Server;
import com.margomarket.listing.auction.AuctionListingPolicy;
import com.margomarket.moderation.ModerationScope;
import com.margomarket.moderation.UserModerationService;
import com.margomarket.shared.ForbiddenOperationException;
import com.margomarket.shared.NotFoundException;
import com.margomarket.user.User;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingService {

    private static final int PAGE_SIZE = 50;

    private final UserModerationService userModeration;
    private final ListingRepository listingRepository;
    private final ListingStatusRepository listingStatusRepository;
    private final Catalog catalog;
    private final FavoriteRepository favoriteRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuctionListingPolicy auctionPolicy;

    public Page<Listing> searchActiveListings(ListingFilter filter) {
        Pageable pageable = PageRequest.of(Math.max(0, filter.getPage() - 1), PAGE_SIZE);

        if (!filter.hasSearch()) {
            return listingRepository.findActiveListingsFilteredWithoutSearch(
                    filter.getServerId(),
                    filter.getItemTypeId(),
                    filter.getRarityId(),
                    filter.getBound(),
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
                filter.getBound(),
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

    public java.util.List<PrivateListingDetails> getPrivateDetails(List<Long> ids) {
        return listingRepository.findAllById(ids).stream().map(l -> new PrivateListingDetails(l.getId(),"AUCTION".equals(l.getSaleMode()) ? null : l.getPrice(),l.getPriceMinor(),l.getGoldAmount(),l.getGoldRateMode(),l.getRateGoldUnits(),l.getRatePriceMinor(),l.getContact(),l.getStatus().getName())).toList();
    }

    public Listing getListing(Long id) {
        return listingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new NotFoundException("Ogłoszenie nie istnieje"));
    }

    public Page<Listing> getUserListings(User user, int page) {
        return listingRepository.findByUserSortedByStatusAndCreatedAtDesc(
                user, PageRequest.of(Math.max(0, page - 1), 20));
    }

    @Transactional
    public Listing createListing(ListingRequest request, User owner) {
        userModeration.requireAllowed(owner,ModerationScope.ITEM_LISTINGS);
        return createItemListing(request,owner);
    }

    @Transactional
    public Listing createAuctionListing(ListingRequest request,User owner) {
        userModeration.requireAllowed(owner,ModerationScope.AUCTION_LISTINGS);
        return createItemListing(request,owner);
    }

    private Listing createItemListing(ListingRequest request,User owner) {
        Listing listing = new Listing();
        listing.setUser(owner);
        applyRequest(listing, request);
        listing.setStatus(getStatus("active"));
        return listingRepository.save(listing);
    }

    @Transactional
    public Listing updateListing(Long id, ListingRequest request, User currentUser) {
        userModeration.requireAllowed(currentUser,ModerationScope.ITEM_LISTINGS);
        Listing listing = lockedListing(id);
        requireOwnerOrAdmin(listing, currentUser);
        auctionPolicy.requireFixed(listing);
        if (listing.isSold()) {
            throw new IllegalArgumentException("Nie można edytować sprzedanego ogłoszenia");
        }
        if (!"ITEM".equals(listing.getListingKind())) throw new IllegalArgumentException("Użyj formularza złota");
        applyRequest(listing, request);
        return listing;
    }

    @Transactional
    public Listing markAsSold(Long id, User currentUser) {
        Listing listing = lockedListing(id);
        requireOwnerOrAdmin(listing, currentUser);
        auctionPolicy.requireSaleAllowed(listing);
        if (listing.isSold()) {
            return listing;
        }
        if ("GOLD".equals(listing.getListingKind()) && !listing.isActive()) throw new IllegalArgumentException("Oferta złota nie jest aktywna");
        listing.setStatus(getStatus("sold"));
        listing.setSoldAt(LocalDateTime.now());
        auctionPolicy.declaredSold(listing,currentUser.getId());
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
        Listing listing = lockedListing(id);
        requireOwnerOrAdmin(listing, currentUser);
        auctionPolicy.requireFixed(listing);
        if (!currentUser.isAdmin() && !listing.isActive()) {
            throw new IllegalArgumentException("Można usunąć tylko aktywne ogłoszenie");
        }
        favoriteRepository.deleteByListingId(listing.getId());
        if (listingRepository.hasExchangeConfirmation(id)) {
            listing.setStatus(getStatus("archived"));
        } else {
            listingRepository.delete(listing);
        }
    }

    public void applyRequest(Listing listing, ListingRequest request) {
        Item item = getItem(request.itemId());
        listing.setItem(item);
        listing.setItemName(item.getName());
        listing.setItemType(item.getItemType());
        listing.setLevel(item.getLevel());
        listing.setEnhancementLevel(ItemRules.isEnhancementLocked(item) || request.enhancementLevel() == null ? 0 : request.enhancementLevel());
        listing.setBound(ItemRules.canBind(item) && request.bound());
        listing.setRarity(item.getRarity());
        Currency currency = getCurrency(request.currencyId());
        if (!"w grze".equalsIgnoreCase(currency.getName().trim()) && request.price() > ListingRequest.MAX_PLN_PRICE) {
            throw new IllegalArgumentException("Cena jest zbyt wysoka");
        }
        listing.setPrice(request.price());
        listing.setCurrency(currency);
        listing.setServer(getServer(request.serverId()));
        listing.setContact(request.contact() == null ? "" : request.contact().trim());
    }

    private Listing lockedListing(Long id) {
        return listingRepository.findLockedById(id).orElseThrow(() -> new NotFoundException("Ogłoszenie nie istnieje"));
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
        return catalog.server(id)
                .orElseThrow(() -> new NotFoundException("Serwer nie istnieje"));
    }

    private Item getItem(Long id) {
        return catalog.item(id)
                .orElseThrow(() -> new NotFoundException("Przedmiot nie istnieje"));
    }

    private Currency getCurrency(Long id) {
        return catalog.currency(id)
                .orElseThrow(() -> new NotFoundException("Waluta nie istnieje"));
    }
}
