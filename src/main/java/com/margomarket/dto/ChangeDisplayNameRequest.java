package com.margomarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangeDisplayNameRequest(
        @NotBlank(message = "Pseudonim jest wymagany")
        @Pattern(regexp = "[\\p{L}\\p{N}_. -]{3,32}", message = "Pseudonim musi mieć 3–32 znaki: litery, cyfry, spacje, _, . lub -")
        String displayName,
        @NotBlank(message = "Obecne hasło jest wymagane")
        String currentPassword
) {}
