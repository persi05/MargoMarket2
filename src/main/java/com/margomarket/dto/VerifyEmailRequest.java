package com.margomarket.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
        @NotBlank @Email(regexp = "^[^\\s@]+@(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,63}$") String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}", message = "Kod musi mieć 6 cyfr") String code
) {}
