package com.margomarket.pricing;

import com.margomarket.catalog.WorldType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** World settings editable by admins; rates are optional, prices are in grosze. */
public record WorldRatesRequest(
        @Size(min = 2, max = 100, message = "Nazwa świata musi mieć od 2 do 100 znaków") String name,
        @NotNull(message = "Wybierz typ świata") WorldType worldType,
        @Positive(message = "Kurs złota musi być dodatni") @Max(value = 1_000_000_000_000L, message = "Kurs złota jest za duży") Long goldPerPln,
        @Positive(message = "Cena ulepy musi być dodatnia") @Max(value = 100_000_000L, message = "Cena ulepy jest za duża") Long upgradeMillionPriceGrosze,
        @Positive(message = "Cena esencji musi być dodatnia") @Max(value = 100_000_000L, message = "Cena esencji jest za duża") Long essencePriceGrosze
) {
}
