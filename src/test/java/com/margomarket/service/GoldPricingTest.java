package com.margomarket.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GoldPricingTest {

    @Test
    void quotesMillionsPerPln() {
        assertThat(GoldPricing.total(260000000L, "M_PER_PLN", 13000000L, 100L)).isEqualTo(2000);
        assertThat(GoldPricing.total(1500000000L, "M_PER_PLN", 20000000L, 100L)).isEqualTo(7500);
    }

    @Test
    void quotesPlnPerBillion() {
        assertThat(GoldPricing.total(1500000000L, "PLN_PER_G", 1000000000L, 2300L)).isEqualTo(3450);
    }

    @Test
    void roundsHalfUpToGrosze() {
        assertThat(GoldPricing.total(15000000L, "PLN_PER_G", 1000000000L, 100L)).isEqualTo(2);
    }

    @Test
    void rejectsZeroOverflowAndWrongUnit() {
        assertThatThrownBy(() -> GoldPricing.total(1L, "PLN_PER_G", 1000000000L, 1L)).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThatThrownBy(() ->
            GoldPricing.total(9000000000000000L, "M_PER_PLN", 1L, 100L)
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GoldPricing.total(1L, "M_PER_PLN", 1L, 99L)).isInstanceOf(
            IllegalArgumentException.class
        );
    }
}
