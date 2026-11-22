package com.margomarket.listing;

public record PrivateListingDetails(
    Long id,
    Long price,
    Long priceMinor,
    Long goldAmount,
    String goldRateMode,
    Long rateGoldUnits,
    Long ratePriceMinor,
    String contact,
    String status
) {}
