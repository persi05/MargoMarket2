package com.margomarket.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoldListingFilter {

    private Long serverId;

    @Min(1)
    @Max(9_000_000_000_000_000L)
    private Long minAmount;

    @Min(1)
    @Max(9_000_000_000_000_000L)
    private Long maxAmount;

    @Min(1)
    @Max(100_000_000)
    private Long minPrice;

    @Min(1)
    @Max(100_000_000)
    private Long maxPrice;

    @Size(max = 200)
    private String search;

    private String sort = "newest";

    @Min(1)
    @Max(1000000)
    private int page = 1;
}
