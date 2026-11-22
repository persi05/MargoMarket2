package com.margomarket.notification.internal;

import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(UserNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getAuctionId()
        );
    }
}
