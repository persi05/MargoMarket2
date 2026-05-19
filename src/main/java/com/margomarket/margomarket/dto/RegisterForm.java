package com.margomarket.margomarket.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterForm {

    @NotBlank(message = "Email jest wymagany")
    @Email(message = "Nieprawidłowy adres email")
    @Size(max = 255, message = "Email jest zbyt długi")
    private String email;

    @NotBlank(message = "Hasło jest wymagane")
    @Size(min = 6, max = 128, message = "Hasło musi mieć od 6 do 128 znaków")
    private String password;

    @NotBlank(message = "Potwierdzenie hasła jest wymagane")
    private String confirmPassword;

    public boolean isPasswordMatching() {
        return password != null && password.equals(confirmPassword);
    }
}