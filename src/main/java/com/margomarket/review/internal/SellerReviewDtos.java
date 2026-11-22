package com.margomarket.review.internal;

import com.margomarket.shared.PageResponse;

import java.time.Instant;
import java.util.List;

public final class SellerReviewDtos {
    private SellerReviewDtos() {}

    public record CreateReviewRequest(Long confirmationId, Integer rating, String comment) {}

    public record HideReviewRequest(String reason) {}

    public record PendingReview(long confirmationId, Long conversationId, long sellerId, String sellerDisplayName, String itemName,
                                Instant confirmedAt, Instant reviewableUntil) {}

    public record SellerReviewResponse(long id, int rating, String comment, long buyerId, String buyerDisplayName,
                                       String itemName, Instant createdAt) {}

    /** distribution[i] = liczba opinii z oceną i+1. */
    public record ReviewSummary(long count, Double average, Integer positivePercent, List<Long> distribution) {}

    public record SellerReviewsResponse(ReviewSummary summary, PageResponse<SellerReviewResponse> reviews) {}
}
