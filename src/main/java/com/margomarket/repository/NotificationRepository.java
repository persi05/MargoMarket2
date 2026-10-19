package com.margomarket.repository;

import com.margomarket.model.User;
import com.margomarket.model.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationRepository extends JpaRepository<UserNotification, Long> {

    Page<UserNotification> findByUserOrderByCreatedAtDescIdDesc(User user, Pageable pageable);

    long countByUserAndReadFalse(User user);
}
