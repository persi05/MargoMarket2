package com.margomarket.margomarket.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateListingForm {

    @NotBlank(message = "Nazwa przedmiotu jest wymagana")
    @Size(min = 3, max = 60, message = "Nazwa musi mieć od 3 do 60 znaków")
    private String itemName;

    @NotNull(message = "Typ przedmiotu jest wymagany")
    private Long itemTypeId;

    @NotNull(message = "Poziom jest wymagany")
    @Min(value = 1, message = "Poziom musi być co najmniej 1")
    @Max(value = 300, message = "Poziom nie może przekraczać 300")
    private Integer level;

    @NotNull(message = "Rzadkość jest wymagana")
    private Long rarityId;

    @NotNull(message = "Cena jest wymagana")
    @Min(value = 1, message = "Cena musi być większa od 0")
    @Max(value = 2_000_000_000, message = "Cena jest zbyt wysoka")
    private Integer price;

    @NotNull(message = "Waluta jest wymagana")
    private Long currencyId;

    @NotNull(message = "Serwer jest wymagany")
    private Long serverId;

    @NotBlank(message = "Dane kontaktowe są wymagane")
    @Size(max = 50, message = "Kontakt może mieć maksymalnie 50 znaków")
    private String contact;
}