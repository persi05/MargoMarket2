package com.margomarket.notification.internal;

import com.margomarket.user.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationRepository extends JpaRepository<UserNotification, Long> {

    Page<UserNotification> findByUserOrderByCreatedAtDescIdDesc(User user, Pageable pageable);

    long countByUserAndReadFalse(User user);
}
