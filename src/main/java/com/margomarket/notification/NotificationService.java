package com.margomarket.notification;

import com.margomarket.notification.internal.NotificationRepository;
import com.margomarket.notification.internal.UserNotification;
import com.margomarket.shared.ForbiddenOperationException;
import com.margomarket.shared.NotFoundException;
import com.margomarket.user.User;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public void auction(long userId,long auctionId,String type,String message,String eventKey) {
        jdbc.update("INSERT INTO notifications(user_id,type,message,is_read,created_at,auction_id,event_key) VALUES (?,?,?,false,clock_timestamp(),?,?) ON CONFLICT(event_key) DO NOTHING",userId,type,message,auctionId,eventKey+":"+userId);
    }

    public void review(long userId,String type,String message,String eventKey) {
        jdbc.update("INSERT INTO notifications(user_id,type,message,is_read,created_at,event_key) VALUES (?,?,?,false,clock_timestamp(),?) ON CONFLICT(event_key) DO NOTHING",userId,type,message,eventKey);
    }

    public Page<UserNotification> getUserNotifications(User user, int page) {
        return notificationRepository.findByUserOrderByCreatedAtDescIdDesc(user,
                PageRequest.of(Math.max(0, page - 1), 20));
    }

    public long countUnread(User user) {
        return notificationRepository.countByUserAndReadFalse(user);
    }

    /**
     * Tells the seller and everyone watching the listing that it was sold. Each notification has a key
     * per listing and user, so a repeated delivery of the same sale event adds nothing.
     */
    @Transactional
    public void createListingSoldNotifications(long listingId, long ownerId, List<Long> observerIds, String itemName) {
        String message = "Ogłoszenie \"" + itemName + "\" zostało oznaczone jako sprzedane.";
        Set<Long> recipientIds = new LinkedHashSet<>();
        recipientIds.add(ownerId);
        recipientIds.addAll(observerIds);
        for (long userId : recipientIds) {
            jdbc.update("INSERT INTO notifications(user_id,type,message,is_read,created_at,event_key) VALUES (?,?,?,false,clock_timestamp(),?) "
                    + "ON CONFLICT(event_key) DO NOTHING", userId, LISTING_SOLD_TYPE, message, listingSoldKey(listingId, userId));
        }
    }

    static String listingSoldKey(long listingId, long userId) {
        return "listing-sold:" + listingId + ":" + userId;
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
