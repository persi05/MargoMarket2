package com.margomarket.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record PlaceBidRequest(@NotNull @Positive Long amount,
        @NotNull @Pattern(regexp="PLN|GOLD") String currency,
        @NotNull UUID idempotencyKey) {}
