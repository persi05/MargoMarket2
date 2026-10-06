package com.margomarket.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record
RegisterRequest(
        @NotBlank(message = "Nazwa użytkownika jest wymagana")
        @Pattern(regexp = "[a-zA-Z0-9_.-]{3,32}", message = "Nazwa musi mieć 3–32 znaki: litery, cyfry, _, . lub -")
        String username,

        @NotBlank(message = "E-mail jest wymagany")
        @Email(regexp = "^[^\\s@]+@(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,63}$",
                message = "Podaj pełny adres e-mail z domeną")
        String email,

        @NotBlank(message = "Hasło jest wymagane")
        @Size(min = 6, max = 72, message = "Hasło musi mieć od 6 do 72 znaków")
        String password
) {
}
