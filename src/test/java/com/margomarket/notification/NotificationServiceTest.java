package com.margomarket.notification;

import com.margomarket.notification.internal.NotificationRepository;
import com.margomarket.notification.internal.UserNotification;
import com.margomarket.shared.ForbiddenOperationException;
import com.margomarket.shared.NotFoundException;
import com.margomarket.user.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void getUserNotificationsReturnsNotificationsSortedByRepository() {
        User user = user(1L);
        UserNotification first = new UserNotification(user, "LISTING_SOLD", "first");
        UserNotification second = new UserNotification(user, "LISTING_SOLD", "second");

        when(notificationRepository.findByUserOrderByCreatedAtDescIdDesc(eq(user), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        Page<UserNotification> notifications = notificationService.getUserNotifications(user, 1);

        assertThat(notifications.getContent()).containsExactly(first, second);
    }

    @Test
    void countUnreadUsesCurrentUser() {
        User user = user(1L);
        when(notificationRepository.countByUserAndReadFalse(user)).thenReturn(3L);

        long count = notificationService.countUnread(user);

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void createListingSoldNotificationsNotifiesOwnerAndObserversOnceEach() {
        notificationService.createListingSoldNotifications(10L, 1L, List.of(2L, 1L), "Smoczy miecz");

        String sql = "INSERT INTO notifications(user_id,type,message,is_read,created_at,event_key) VALUES (?,?,?,false,clock_timestamp(),?) "
                + "ON CONFLICT(event_key) DO NOTHING";
        String message = "Ogłoszenie \"Smoczy miecz\" zostało oznaczone jako sprzedane.";
        verify(jdbc).update(sql, 1L, "LISTING_SOLD", message, "listing-sold:10:1");
        verify(jdbc).update(sql, 2L, "LISTING_SOLD", message, "listing-sold:10:2");
        verifyNoMoreInteractions(jdbc);
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
