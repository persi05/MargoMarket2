package com.margomarket.listing.internal;

import com.margomarket.listing.AuctionSummary;
import com.margomarket.listing.Listing;
import com.margomarket.listing.ListingResponse;
import com.margomarket.catalog.LookupResponse;
import com.margomarket.user.PublicNames;

import org.springframework.stereotype.Component;

@Component
public class ListingMapper {

    public ListingResponse toResponse(Listing listing) {
        return toResponse(listing, true);
    }

    public ListingResponse toResponse(Listing listing, boolean showPrivateDetails) {
        return toResponse(listing, showPrivateDetails, null);
    }

    public ListingResponse toResponse(Listing listing, boolean showPrivateDetails, AuctionSummary auction) {
        AuctionSummary visibleAuction = auction == null || showPrivateDetails ? auction
                : new AuctionSummary(auction.currency(), null, null, auction.bidCount(), auction.endsAt(), auction.status());
        return new ListingResponse(
                listing.getId(),
                listing.getItem() == null ? null : listing.getItem().getId(),
                listing.getItemName(),
                listing.getItem() == null ? null : listing.getItem().getIconUrl(),
                listing.getItem() == null ? null : listing.getItem().getDescription(),
                listing.getItem() == null ? null : listing.getItem().getStats(),
                listing.getItemType() == null ? null : LookupResponse.of(listing.getItemType()),
                listing.getLevel(),
                listing.getEnhancementLevel(),
                listing.isBound(),
                listing.getRarity() == null ? null : LookupResponse.of(listing.getRarity()),
                showPrivateDetails && !"AUCTION".equals(listing.getSaleMode()) ? listing.getPrice() : null,
                LookupResponse.of(listing.getCurrency()),
                LookupResponse.of(listing.getServer()),
                showPrivateDetails ? listing.getContact() : null,
                listing.getStatus().getName(),
                listing.getUser().getId(),
                PublicNames.name(listing.getUser()),
                listing.getCreatedAt(),
                listing.getListingKind(),
                listing.getGoldAmount(),
                showPrivateDetails ? listing.getPriceMinor() : null,
                listing.getGoldRateMode(),
                showPrivateDetails ? listing.getRateGoldUnits() : null,
                showPrivateDetails ? listing.getRatePriceMinor() : null,
                listing.getDescription(),
                listing.getSoldAt(),
                listing.getSaleMode(),
                visibleAuction
        );
    }
}
