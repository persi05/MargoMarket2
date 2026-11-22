package com.margomarket.listing;

import com.margomarket.catalog.LookupResponse;

import java.time.LocalDateTime;

public record ListingResponse(
        Long id,
        Long itemId,
        String itemName,
        String iconUrl,
        String itemDescription,
        String itemStats,
        LookupResponse itemType,
        Integer level,
        Integer enhancementLevel,
        boolean bound,
        LookupResponse rarity,
        Long price,
        LookupResponse currency,
        LookupResponse server,
        String contact,
        String status,
        Long sellerId,
        String sellerDisplayName,
        LocalDateTime createdAt,
        String listingKind,
        Long goldAmount,
        Long priceMinor,
        String goldRateMode,
        Long rateGoldUnits,
        Long ratePriceMinor,
        String description,
        LocalDateTime soldAt,
        String saleMode,
        AuctionSummary auction
) {
}
