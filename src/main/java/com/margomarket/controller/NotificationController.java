package com.margomarket.controller;

import com.margomarket.dto.NotificationResponse;
import com.margomarket.mapper.NotificationMapper;
import com.margomarket.model.User;
import com.margomarket.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    @GetMapping
    public List<NotificationResponse> mine(@AuthenticationPrincipal User user) {
        return notificationService.getUserNotifications(user).stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal User user) {
        return Map.of("count", notificationService.countUnread(user));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable Long id,
                                           @AuthenticationPrincipal User user) {
        return notificationMapper.toResponse(notificationService.markAsRead(id, user));
    }
}
