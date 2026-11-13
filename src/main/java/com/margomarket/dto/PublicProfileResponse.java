package com.margomarket.dto;
import java.time.LocalDateTime;
public record PublicProfileResponse(Long id, String displayName, LocalDateTime createdAt,
 long activeListings, long soldListings, long confirmedPurchases, long confirmedSales, long endedAuctions) {}
