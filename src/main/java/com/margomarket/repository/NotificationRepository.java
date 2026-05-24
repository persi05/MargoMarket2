package com.margomarket.repository;

import com.margomarket.model.User;
import com.margomarket.model.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<UserNotification, Long> {

    List<UserNotification> findByUserOrderByCreatedAtDesc(User user);

    long countByUserAndReadFalse(User user);
}
