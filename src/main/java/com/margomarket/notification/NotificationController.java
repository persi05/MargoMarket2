package com.margomarket.notification;

import com.margomarket.shared.PageMapper;
import com.margomarket.shared.PageResponse;
import com.margomarket.user.User;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Powiadomienia", description = "Powiadomienia użytkownika")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;
    private final PageMapper pageMapper;

    @GetMapping
    public PageResponse<NotificationResponse> mine(@RequestParam(defaultValue = "1") int page,
                                                    @AuthenticationPrincipal User user) {
        return pageMapper.toResponse(notificationService.getUserNotifications(user, page),
                notificationMapper::toResponse);
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
