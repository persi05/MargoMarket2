package com.margomarket.event;

import com.margomarket.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListingSoldNotificationListener {

    private final NotificationService notificationService;

    @EventListener
    public void handleListingSold(ListingSoldEvent event) {
        notificationService.createListingSoldNotifications(event.ownerId(), event.observerIds(), event.itemName());
    }
}
