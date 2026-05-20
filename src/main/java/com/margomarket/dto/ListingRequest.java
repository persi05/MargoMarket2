package com.margomarket.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ListingRequest(
        @NotBlank(message = "Nazwa przedmiotu jest wymagana")
        @Size(min = 3, max = 60, message = "Nazwa musi mieć od 3 do 60 znaków")
        String itemName,

        @NotNull(message = "Typ przedmiotu jest wymagany")
        Long itemTypeId,

        @NotNull(message = "Poziom jest wymagany")
        @Min(value = 1, message = "Poziom musi być co najmniej 1")
        @Max(value = 300, message = "Poziom nie może przekraczać 300")
        Integer level,

        @NotNull(message = "Rzadkość jest wymagana")
        Long rarityId,

        @NotNull(message = "Cena jest wymagana")
        @Min(value = 1, message = "Cena musi być większa od 0")
        @Max(value = 2_000_000_000, message = "Cena jest zbyt wysoka")
        Integer price,

        @NotNull(message = "Waluta jest wymagana")
        Long currencyId,

        @NotNull(message = "Serwer jest wymagany")
        Long serverId,

        @NotBlank(message = "Kontakt jest wymagany")
        @Size(max = 50, message = "Kontakt może mieć maksymalnie 50 znaków")
        String contact
) {
}
