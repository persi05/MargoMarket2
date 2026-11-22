package com.margomarket.listing.internal;

import com.margomarket.listing.ListingSoldEvent;
import com.margomarket.notification.NotificationService;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Tells the seller and everyone watching the listing that it was sold, after the sale commits. */
@Component
@RequiredArgsConstructor
public class ListingSoldNotificationListener {

    private final NotificationService notificationService;

    @ApplicationModuleListener
    public void handleListingSold(ListingSoldEvent event) {
        notificationService.createListingSoldNotifications(event.ownerId(), event.observerIds(), event.itemName());
    }
}
