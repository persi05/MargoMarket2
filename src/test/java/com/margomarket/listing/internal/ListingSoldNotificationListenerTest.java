package com.margomarket.listing.internal;

import com.margomarket.listing.ListingSoldEvent;
import com.margomarket.notification.NotificationService;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ListingSoldNotificationListenerTest {

    @Test
    void handleListingSoldDelegatesToNotificationServiceWithOwnerAndObservers() {
        NotificationService notifications = mock(NotificationService.class);
        ListingSoldNotificationListener listener = new ListingSoldNotificationListener(notifications);

        listener.handleListingSold(new ListingSoldEvent(10L, 1L, List.of(2L, 3L), "Smoczy miecz"));

        verify(notifications).createListingSoldNotifications(10L, 1L, List.of(2L, 3L), "Smoczy miecz");
    }
}
