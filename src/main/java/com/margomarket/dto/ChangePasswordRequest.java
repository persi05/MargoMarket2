package com.margomarket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Obecne hasło jest wymagane")
        String currentPassword,
        @NotBlank(message = "Nowe hasło jest wymagane")
        @Size(min = 6, max = 72, message = "Hasło musi mieć od 6 do 72 znaków")
        String newPassword
) {}
