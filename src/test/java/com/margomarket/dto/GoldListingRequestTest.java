package com.margomarket.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class GoldListingRequestTest {

    @Test
    void validatesBounds() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(
                validator.validate(
                    new GoldListingRequest(
                        1L,
                        1500000000L,
                        "PLN_PER_G",
                        1000000000L,
                        2300L,
                        null,
                        null
                    )
                )
            ).isEmpty();
            for (long amount : new long[] { 0, -1, 9000000000000001L })
                assertThat(
                    validator.validate(
                        new GoldListingRequest(1L, amount, "M_PER_PLN", 13000000L, 100L, null, null)
                    )
                ).isNotEmpty();
            for (long price : new long[] { 0, -1, 100000001L })
                assertThat(
                    validator.validate(
                        new GoldListingRequest(1L, 1L, "PLN_PER_G", 1000000000L, price, null, null)
                    )
                ).isNotEmpty();
        }
    }
}
