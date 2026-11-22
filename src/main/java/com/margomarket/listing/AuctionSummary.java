package com.margomarket.listing;

import java.time.Instant;

public record AuctionSummary(String currency, Long startingAmount, Long highestAmount,
                             long bidCount, Instant endsAt, String status) {}
