package com.margomarket.dto;

import jakarta.validation.constraints.*;

public record GoldListingRequest(
    @NotNull Long serverId,
    @NotNull @Min(1) @Max(9_000_000_000_000_000L) Long goldAmount,
    @NotNull @Pattern(regexp = "M_PER_PLN|PLN_PER_G") String rateMode,
    @NotNull @Min(1) @Max(9_000_000_000_000_000L) Long rateGoldUnits,
    @NotNull @Min(1) @Max(100_000_000) Long ratePriceMinor,
    @Size(max = 2000) String description,
    @Size(max = 50) String contact
) {}
