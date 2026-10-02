package com.margomarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StartConversationRequest(
        @NotNull Long listingId,
        @NotBlank @Size(max = 2000) String body
) {
}
