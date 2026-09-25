package com.margomarket.dto;

import java.time.LocalDateTime;

public record ListingCommentResponse(
        Long id,
        Long authorId,
        boolean seller,
        String body,
        LocalDateTime createdAt,
        boolean canDelete
) {
}
