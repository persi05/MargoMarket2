package com.margomarket.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email jest wymagany")
        @Email(message = "Email ma niepoprawny format")
        String email,

        @NotBlank(message = "Hasło jest wymagane")
        String password
) {
}
