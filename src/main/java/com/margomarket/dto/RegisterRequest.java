package com.margomarket.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email jest wymagany")
        @Email(message = "Email ma niepoprawny format")
        String email,

        @NotBlank(message = "Hasło jest wymagane")
        @Size(min = 6, max = 72, message = "Hasło musi mieć od 6 do 72 znaków")
        String password
) {
}
