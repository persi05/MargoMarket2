package com.margomarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(@NotBlank @Size(max = 2000) String body) {
}
