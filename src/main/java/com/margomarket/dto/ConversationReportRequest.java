package com.margomarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationReportRequest(
        @NotBlank String reason,
        @Size(max = 1000) String details
) {}
