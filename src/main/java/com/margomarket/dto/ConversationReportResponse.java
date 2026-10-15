package com.margomarket.dto;

import java.time.LocalDateTime;

public record ConversationReportResponse(
        Long id,
        Long conversationId,
        String itemName,
        String buyerDisplayName,
        String sellerDisplayName,
        String reporterDisplayName,
        String reportedUserDisplayName,
        String reason,
        String details,
        String status,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {}
