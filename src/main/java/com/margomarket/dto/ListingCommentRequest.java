package com.margomarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ListingCommentRequest(
        @NotBlank(message = "Wpisz komentarz.")
        @Size(max = 1000, message = "Komentarz może mieć maksymalnie 1000 znaków.")
        String body
) {
}
