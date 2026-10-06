package com.margomarket.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsEmailWithoutTopLevelDomain() {
        var violations = validator.validate(new RegisterRequest("gracz", "patryk@interia", "haslo123"));
        assertThat(violations).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("email"));
    }

    @Test
    void acceptsCompleteEmailWithSubdomain() {
        assertThat(validator.validate(new RegisterRequest("gracz", "patryk+test@poczta.example.com", "haslo123")))
                .isEmpty();
    }
}
