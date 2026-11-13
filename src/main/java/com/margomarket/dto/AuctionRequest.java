package com.margomarket.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record AuctionRequest(
        @NotNull @Positive Long itemId,
        @NotNull @Positive Long serverId,
        @NotNull @Min(0) @Max(5) Integer enhancementLevel,
        boolean bound,
        @NotNull @Pattern(regexp="PLN|GOLD") String currency,
        @NotNull @Positive Long startingAmount,
        @NotNull Instant endsAt,
        @Size(max=2000) String description,
        @Size(max=50) String contact) {}
