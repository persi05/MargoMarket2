package com.margomarket.listing.internal;

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
