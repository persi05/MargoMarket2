package com.margomarket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

/** Price of 100 dragon scales in grosze; null clears it. */
public record PricingSettingsRequest(
        @Positive(message = "Cena SŁ musi być dodatnia") @Max(value = 100_000_000L, message = "Cena SŁ jest za duża") Long scalesHundredPriceGrosze
) {
}
