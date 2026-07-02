package com.margomarket.dto;

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
        LookupResponse rarity,
        Integer price,
        LookupResponse currency,
        LookupResponse server,
        String contact,
        String status,
        Long sellerId,
        String sellerEmail,
        LocalDateTime createdAt,
        LocalDateTime soldAt
) {
}
