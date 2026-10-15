package com.margomarket.dto;

import java.time.LocalDateTime;

public record ConversationResponse(
        Long id,
        Long listingId,
        String itemName,
        Long buyerId,
        String buyerDisplayName,
        Long sellerId,
        String sellerDisplayName,
        Long intermediaryId,
        String intermediaryDisplayName,
        String intermediaryStatus,
        LocalDateTime updatedAt,
        String lastMessage,
        long unreadCount,
        LocalDateTime closedAt,
        String contactRequestStatus,
        Long contactRequesterId,
        boolean contactRequestAllowed
) {
}
