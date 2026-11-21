package com.margomarket.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnbindCostCalculatorTest {
    @Test
    void addsSevenAndAHalfScalesPerLevelToTheBase() {
        assertThat(UnbindCostCalculator.scales("Zwykły", 1)).hasValue(758);
        assertThat(UnbindCostCalculator.scales("Zwykły", 100)).hasValue(1500);
    }

    @Test
    void capsTheBaseAt2250ScalesBeforeTheRaritySurcharge() {
        assertThat(UnbindCostCalculator.scales("Zwykły", 200)).hasValue(2250);
        assertThat(UnbindCostCalculator.scales("Zwykły", 300)).hasValue(2250);
        assertThat(UnbindCostCalculator.scales("Legendarny", 300)).hasValue(6750);
    }

    @Test
    void appliesTheRaritySurcharge() {
        assertThat(UnbindCostCalculator.scales("Unikatowy", 100)).hasValue(1800);
        assertThat(UnbindCostCalculator.scales("Heroiczny", 100)).hasValue(2250);
        assertThat(UnbindCostCalculator.scales("Legendarny", 100)).hasValue(4500);
    }

    @Test
    void hasNoCostForARarityWithoutAKnownSurcharge() {
        assertThat(UnbindCostCalculator.scales("Ulepszony", 100)).isEmpty();
        assertThat(UnbindCostCalculator.scales(null, 100)).isEmpty();
    }

    @Test
    void pricesScalesAtEightyPerZloty() {
        assertThat(UnbindCostCalculator.grosze("Legendarny", 100)).hasValue(5_625);
        assertThat(UnbindCostCalculator.grosze("Legendarny", 300)).hasValue(8_438);
        assertThat(UnbindCostCalculator.grosze("Ulepszony", 100)).isEmpty();
    }
}
