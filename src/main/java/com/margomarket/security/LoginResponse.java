package com.margomarket.security;

import com.margomarket.user.UserResponse;

public record LoginResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        UserResponse user
) {
}
