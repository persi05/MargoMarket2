package com.margomarket.dto;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long id,
        Long senderId,
        String senderDisplayName,
        String senderRole,
        String kind,
        String body,
        LocalDateTime createdAt
) {
}
