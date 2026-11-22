package com.margomarket.review.internal;

import com.margomarket.conversation.ExchangeConfirmed;
import com.margomarket.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Invites the buyer to review the seller once an intermediary confirms their exchange. */
@Component
@RequiredArgsConstructor
class ReviewPrompt {
    private final NotificationService notifications;

    @EventListener
    void exchangeConfirmed(ExchangeConfirmed event) {
        notifications.review(event.buyerId(), "REVIEW_AVAILABLE",
                "Wymiana „" + event.itemName() + "” została potwierdzona. Możesz wystawić opinię sprzedawcy w ciągu "
                        + SellerReviewService.REVIEW_WINDOW.toDays() + " dni.",
                "review-available:" + event.confirmationId());
    }
}
