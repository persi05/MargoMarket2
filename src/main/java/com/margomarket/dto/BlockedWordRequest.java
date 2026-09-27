package com.margomarket.dto;

import com.margomarket.model.BlockedWord.MatchMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BlockedWordRequest(
        @NotBlank @Size(max = 80) String term,
        @NotNull MatchMode matchMode,
        @NotNull Boolean enabled
) {}
