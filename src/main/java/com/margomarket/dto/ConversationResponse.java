package com.margomarket.dto;

import java.time.LocalDateTime;

public record ConversationResponse(
        Long id,
        Long listingId,
        String itemName,
        Long buyerId,
        Long sellerId,
        Long intermediaryId,
        String intermediaryStatus,
        LocalDateTime updatedAt,
        String lastMessage,
        long unreadCount
) {
}
