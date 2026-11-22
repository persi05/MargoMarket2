package com.margomarket.user;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String displayName,
        String email,
        String role,
        LocalDateTime createdAt
) {
    public static UserResponse of(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                user.getRole().getName(), user.getCreatedAt());
    }
}
