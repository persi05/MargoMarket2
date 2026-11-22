package com.margomarket.security.internal;

import com.margomarket.user.UserResponse;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        UserResponse user
) {
}
