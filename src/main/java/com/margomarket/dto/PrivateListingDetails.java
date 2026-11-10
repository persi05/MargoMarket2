package com.margomarket.dto;

public record PrivateListingDetails(
    Long id,
    Integer price,
    Long priceMinor,
    Long goldAmount,
    String goldRateMode,
    Long rateGoldUnits,
    Long ratePriceMinor,
    String contact,
    String status
) {}
