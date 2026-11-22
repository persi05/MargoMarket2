package com.margomarket.listing.comment;

import java.time.LocalDateTime;

public record ListingCommentResponse(
        Long id,
        Long authorId,
        String authorDisplayName,
        boolean seller,
        String body,
        LocalDateTime createdAt,
        boolean canDelete
) {
}
