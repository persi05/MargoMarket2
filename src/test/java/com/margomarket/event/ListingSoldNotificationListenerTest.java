package com.margomarket.event;

import com.margomarket.model.UserNotification;
import com.margomarket.repository.NotificationRepository;
import com.margomarket.repository.UserRepository;
import com.margomarket.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ListingSoldNotificationListenerTest {

    @Test
    void handleListingSoldDelegatesToNotificationServiceWithOwnerAndObservers() {
        RecordingNotificationService notificationService = new RecordingNotificationService();
        ListingSoldNotificationListener listener = new ListingSoldNotificationListener(notificationService);
        ListingSoldEvent event = new ListingSoldEvent(10L, 1L, List.of(2L, 3L), "Smoczy miecz");

        listener.handleListingSold(event);

        assertThat(notificationService.ownerId).isEqualTo(1L);
        assertThat(notificationService.observerIds).containsExactly(2L, 3L);
        assertThat(notificationService.itemName).isEqualTo("Smoczy miecz");
    }

    private static final class RecordingNotificationService extends NotificationService {
        private Long ownerId;
        private List<Long> observerIds;
        private String itemName;

        private RecordingNotificationService() {
            super(nullRepository(), nullRepository());
        }

        @Override
        public List<UserNotification> createListingSoldNotifications(Long ownerId, List<Long> observerIds, String itemName) {
            this.ownerId = ownerId;
            this.observerIds = observerIds;
            this.itemName = itemName;
            return List.of();
        }

        private static <T> T nullRepository() {
            return null;
        }
    }
}
