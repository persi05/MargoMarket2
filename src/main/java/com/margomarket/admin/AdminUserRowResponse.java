package com.margomarket.admin;

import com.margomarket.user.UserResponse;
import com.margomarket.listing.UserStats;

public record AdminUserRowResponse(UserResponse user, UserStats stats) {
}
