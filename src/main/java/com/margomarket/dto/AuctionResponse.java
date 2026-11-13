package com.margomarket.dto;

import java.time.Instant;

public record AuctionResponse(Long id, ListingResponse listing, String currency, Long startingAmount,
        Long highestAmount, Long nextMinimumBid, Instant originalEndsAt, Instant endsAt,
        Instant serverTime, String status, long version, long bidCount, boolean leading,
        boolean owner, boolean winner, String fulfillmentStatus, Long conversationId,
        boolean contactReminder, Long winnerId, String winnerDisplayName) {}
