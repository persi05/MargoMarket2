package com.margomarket.service;

import com.margomarket.exception.ForbiddenOperationException;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.User;
import com.margomarket.model.UserNotification;
import com.margomarket.repository.NotificationRepository;
import com.margomarket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String LISTING_SOLD_TYPE = "LISTING_SOLD";

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public List<UserNotification> getUserNotifications(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public long countUnread(User user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }

    @Transactional
    public List<UserNotification> createListingSoldNotifications(Long ownerId, List<Long> observerIds, String itemName) {
        String message = "Ogłoszenie \"" + itemName + "\" zostało oznaczone jako sprzedane.";
        Set<Long> recipientIds = new LinkedHashSet<>();
        recipientIds.add(ownerId);
        recipientIds.addAll(observerIds);

        List<UserNotification> notifications = recipientIds.stream()
                .map(userRepository::getReferenceById)
                .map(user -> new UserNotification(user, LISTING_SOLD_TYPE, message))
                .toList();

        return notificationRepository.saveAll(notifications);
    }

    @Transactional
    public UserNotification markAsRead(Long notificationId, User currentUser) {
        UserNotification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Powiadomienie nie istnieje"));

        if (!notification.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("Brak uprawnień do tego powiadomienia");
        }

        notification.setRead(true);
        return notification;
    }
}
