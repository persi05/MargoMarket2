package com.margomarket.mapper;

import com.margomarket.dto.ListingResponse;
import com.margomarket.model.Listing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListingMapper {

    private final LookupMapper lookupMapper;

    public ListingResponse toResponse(Listing listing) {
        return toResponse(listing, true);
    }

    public ListingResponse toResponse(Listing listing, boolean showPrivateDetails) {
        return new ListingResponse(
                listing.getId(),
                listing.getItem() == null ? null : listing.getItem().getId(),
                listing.getItemName(),
                listing.getItem() == null ? null : listing.getItem().getIconUrl(),
                listing.getItem() == null ? null : listing.getItem().getDescription(),
                listing.getItem() == null ? null : listing.getItem().getStats(),
                listing.getItemType() == null ? null : lookupMapper.toResponse(listing.getItemType()),
                listing.getLevel(),
                listing.getEnhancementLevel(),
                listing.isBound(),
                listing.getRarity() == null ? null : lookupMapper.toResponse(listing.getRarity()),
                showPrivateDetails && !"AUCTION".equals(listing.getSaleMode()) ? listing.getPrice() : null,
                lookupMapper.toResponse(listing.getCurrency()),
                lookupMapper.toResponse(listing.getServer()),
                showPrivateDetails ? listing.getContact() : null,
                listing.getStatus().getName(),
                listing.getUser().getId(),
                null,
                com.margomarket.service.PublicNames.name(listing.getUser()),
                listing.getCreatedAt(),
                listing.getListingKind(),
                listing.getGoldAmount(),
                showPrivateDetails ? listing.getPriceMinor() : null,
                listing.getGoldRateMode(),
                showPrivateDetails ? listing.getRateGoldUnits() : null,
                showPrivateDetails ? listing.getRatePriceMinor() : null,
                listing.getDescription(),
                listing.getSoldAt(),
                listing.getSaleMode()
        );
    }
}
