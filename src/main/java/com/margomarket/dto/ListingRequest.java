package com.margomarket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ListingRequest(
        @NotNull(message = "Wybierz przedmiot z katalogu")
        Long itemId,

        @NotBlank(message = "Nazwa przedmiotu jest wymagana")
        @Size(min = 3, max = 255, message = "Nazwa musi miec od 3 do 255 znakow")
        String itemName,

        @NotNull(message = "Typ przedmiotu jest wymagany")
        Long itemTypeId,

        @NotNull(message = "Poziom jest wymagany")
        @Min(value = 1, message = "Poziom musi byc co najmniej 1")
        @Max(value = 300, message = "Poziom nie moze przekraczac 300")
        Integer level,

        @Min(value = 0, message = "Poziom ulepszenia musi byc co najmniej 0")
        @Max(value = 5, message = "Poziom ulepszenia nie moze przekraczac 5")
        Integer enhancementLevel,

        boolean bound,

        @NotNull(message = "Rzadkosc jest wymagana")
        Long rarityId,

        @NotNull(message = "Cena jest wymagana")
        @Min(value = 1, message = "Cena musi byc wieksza od 0")
        @Max(value = ListingRequest.MAX_GOLD_PRICE, message = "Cena jest zbyt wysoka")
        Long price,

        @NotNull(message = "Waluta jest wymagana")
        Long currencyId,

        @NotNull(message = "Serwer jest wymagany")
        Long serverId,

        @Size(max = 50, message = "Kontakt moze miec maksymalnie 50 znakow")
        String contact
) {
    /** Gold listing prices are single gold units; 10 000g leaves plenty of room above real offers. */
    public static final long MAX_GOLD_PRICE = 10_000_000_000_000L;
    /** PLN listing prices are whole złoty and keep their previous limit. */
    public static final long MAX_PLN_PRICE = 2_000_000_000L;
}
