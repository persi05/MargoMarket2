package com.margomarket.service;

import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.User;
import com.margomarket.model.UserNotification;
import com.margomarket.repository.NotificationRepository;
import com.margomarket.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void getUserNotificationsReturnsNotificationsSortedByRepository() {
        User user = user(1L);
        UserNotification first = new UserNotification(user, "LISTING_SOLD", "first");
        UserNotification second = new UserNotification(user, "LISTING_SOLD", "second");

        when(notificationRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(first, second));

        List<UserNotification> notifications = notificationService.getUserNotifications(user);

        assertThat(notifications).containsExactly(first, second);
    }

    @Test
    void countUnreadUsesCurrentUser() {
        User user = user(1L);
        when(notificationRepository.countByUserAndReadFalse(user)).thenReturn(3L);

        long count = notificationService.countUnread(user);

        assertThat(count).isEqualTo(3L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createListingSoldNotificationsSavesNotificationForOwnerAndObservers() {
        User owner = user(1L);
        User observer = user(2L);
        when(userRepository.getReferenceById(1L)).thenReturn(owner);
        when(userRepository.getReferenceById(2L)).thenReturn(observer);
        when(notificationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<UserNotification> notifications = notificationService.createListingSoldNotifications(1L, List.of(2L), "Smoczy miecz");

        ArgumentCaptor<List<UserNotification>> notificationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(notificationsCaptor.capture());
        List<UserNotification> savedNotifications = notificationsCaptor.getValue();

        assertThat(notifications).hasSize(2);
        assertThat(savedNotifications).hasSize(2);
        assertThat(savedNotifications).extracting(UserNotification::getUser).containsExactly(owner, observer);
        assertThat(savedNotifications).allSatisfy(notification -> {
            assertThat(notification.getType()).isEqualTo("LISTING_SOLD");
            assertThat(notification.getMessage()).contains("Smoczy miecz");
            assertThat(notification.isRead()).isFalse();
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void createListingSoldNotificationsDoesNotDuplicateOwner() {
        User owner = user(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(owner);
        when(notificationRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<UserNotification> notifications = notificationService.createListingSoldNotifications(1L, List.of(1L), "Smoczy miecz");

        ArgumentCaptor<List<UserNotification>> notificationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(notificationsCaptor.capture());

        assertThat(notifications).hasSize(1);
        assertThat(notifications.getFirst().getUser()).isSameAs(owner);
        assertThat(notificationsCaptor.getValue()).hasSize(1);
        assertThat(notificationsCaptor.getValue().getFirst().getUser()).isSameAs(owner);
    }

    @Test
    void markAsReadSetsReadForNotificationOwner() {
        User owner = user(1L);
        UserNotification notification = new UserNotification(owner, "LISTING_SOLD", "message");

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        UserNotification updated = notificationService.markAsRead(10L, owner);

        assertThat(updated.isRead()).isTrue();
    }

    @Test
    void markAsReadRejectsDifferentUser() {
        User owner = user(1L);
        User stranger = user(2L);
        UserNotification notification = new UserNotification(owner, "LISTING_SOLD", "message");

        when(notificationRepository.findById(10L)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead(10L, stranger))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void markAsReadThrowsWhenNotificationDoesNotExist() {
        when(notificationRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(10L, user(1L)))
                .isInstanceOf(NotFoundException.class);
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
