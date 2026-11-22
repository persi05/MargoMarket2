package com.margomarket.security;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Nazwa użytkownika jest wymagana")
        String username,

        @NotBlank(message = "Hasło jest wymagane")
        String password
) {
}
