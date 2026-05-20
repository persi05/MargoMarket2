package com.margomarket.dto;

import java.time.LocalDateTime;

public record ListingResponse(
        Long id,
        String itemName,
        LookupResponse itemType,
        Integer level,
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
