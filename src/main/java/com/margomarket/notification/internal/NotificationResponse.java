package com.margomarket.notification.internal;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String message,
        boolean read,
        LocalDateTime createdAt,
        Long auctionId
) {
}
