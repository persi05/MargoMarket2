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

        @NotNull(message = "Rzadkosc jest wymagana")
        Long rarityId,

        @NotNull(message = "Cena jest wymagana")
        @Min(value = 1, message = "Cena musi byc wieksza od 0")
        @Max(value = 2_000_000_000, message = "Cena jest zbyt wysoka")
        Integer price,

        @NotNull(message = "Waluta jest wymagana")
        Long currencyId,

        @NotNull(message = "Serwer jest wymagany")
        Long serverId,

        @NotBlank(message = "Kontakt jest wymagany")
        @Size(max = 50, message = "Kontakt moze miec maksymalnie 50 znakow")
        String contact
) {
}
