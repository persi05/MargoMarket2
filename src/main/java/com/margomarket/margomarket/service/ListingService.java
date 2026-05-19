package com.margomarket.margomarket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.margomarket.margomarket.dto.CreateListingForm;
import com.margomarket.margomarket.dto.ListingFilter;
import com.margomarket.margomarket.model.*;
import com.margomarket.margomarket.repository.*;

import java.util.List;
import java.util.Optional;

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

    public List<Server> getServers() { return serverRepository.findAllByOrderByNameAsc();    }
    public List<ItemType> getItemTypes() { return itemTypeRepository.findAllByOrderByNameAsc();  }
    public List<Rarity> getRarities() { return rarityRepository.findAllByOrderByIdAsc();      }
    public List<Currency> getCurrencies() { return currencyRepository.findAllByOrderByIdAsc();    }

    public Page<Listing> searchActiveListings(ListingFilter filter) {
        Pageable pageable = PageRequest.of(Math.max(0, filter.getPage() - 1), PAGE_SIZE);

        return listingRepository.findActiveListingsFiltered(
                filter.getNullableSearch(),
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

        String statusName = (filter.getStatus() != null && !filter.getStatus().isBlank())
                ? filter.getStatus()
                : null;

        return listingRepository.findAllListingsAdmin(
                filter.getNullableSearch(),
                filter.getServerId(),
                statusName,
                pageable
        );
    }

    public long countAllAdmin(ListingFilter filter) {
        return searchAllListingsAdmin(filter).getTotalElements();
    }

    public List<Listing> getUserListings(User user) {
        return listingRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public Listing createListing(CreateListingForm form, User owner) {
        ListingStatus activeStatus = listingStatusRepository.findByName("active")
                .orElseThrow(() -> new IllegalStateException("Status 'active' not found"));

        Server server = serverRepository.findById(form.getServerId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid server"));
        ItemType itemType = itemTypeRepository.findById(form.getItemTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid item type"));
        Rarity rarity   = rarityRepository.findById(form.getRarityId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid rarity"));
        Currency currency = currencyRepository.findById(form.getCurrencyId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid currency"));

        Listing listing = new Listing();
        listing.setUser(owner);
        listing.setItemName(form.getItemName().trim());
        listing.setItemType(itemType);
        listing.setLevel(form.getLevel());
        listing.setRarity(rarity);
        listing.setPrice(form.getPrice());
        listing.setCurrency(currency);
        listing.setServer(server);
        listing.setContact(form.getContact().trim());
        listing.setStatus(activeStatus);

        return listingRepository.save(listing);
    }

    @Transactional
    public boolean markAsSold(Long listingId, Long userId) {
        try {
            Boolean result = listingRepository.markAsSoldDb(listingId, userId);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public boolean deleteListing(Long listingId, Long userId) {
        return listingRepository.deleteByIdAndUserId(listingId, userId) > 0;
    }

    @Transactional
    public boolean deleteListingAdmin(Long listingId) {
        if (!listingRepository.existsById(listingId)) return false;
        listingRepository.deleteById(listingId);
        return true;
    }

    public Optional<Listing> findById(Long id) {
        return listingRepository.findById(id);
    }
}