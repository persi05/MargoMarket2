package com.margomarket.review.internal;

import com.margomarket.conversation.ExchangeConfirmed;
import com.margomarket.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Invites the buyer to review the seller once an intermediary confirms their exchange.
 * Runs after the confirmation commits; the event key makes a retried delivery harmless.
 */
@Component
@RequiredArgsConstructor
class ReviewPrompt {
    private final NotificationService notifications;

    @ApplicationModuleListener
    public void exchangeConfirmed(ExchangeConfirmed event) {
        notifications.review(event.buyerId(), "REVIEW_AVAILABLE",
                "Wymiana „" + event.itemName() + "” została potwierdzona. Możesz wystawić opinię sprzedawcy w ciągu "
                        + SellerReviewService.REVIEW_WINDOW.toDays() + " dni.",
                "review-available:" + event.confirmationId());
    }
}
