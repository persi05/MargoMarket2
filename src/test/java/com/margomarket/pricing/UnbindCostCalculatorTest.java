package com.margomarket.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnbindCostCalculatorTest {
    @Test
    void addsSevenAndAHalfScalesPerLevelRoundedToSeventyFive() {
        assertThat(UnbindCostCalculator.scales("Zwykły", 1)).hasValue(750);
        assertThat(UnbindCostCalculator.scales("Zwykły", 6)).hasValue(825);
        assertThat(UnbindCostCalculator.scales("Zwykły", 100)).hasValue(1500);
    }

    @Test
    void matchesTheInGamePriceOfLowLevelLegendaries() {
        assertThat(UnbindCostCalculator.scales("Legendarny", 22)).hasValue(2775);
        assertThat(UnbindCostCalculator.scales("Legendarny", 23)).hasValue(2775);
        assertThat(UnbindCostCalculator.scales("Legendarny", 24)).hasValue(2775);
        assertThat(UnbindCostCalculator.scales("Legendarny", 40)).hasValue(3150);
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
    void pricesScalesAtEightyPerZlotyRoundedUpToWholeZloty() {
        // 4500 SŁ = 56,25 zł -> 57 zł; 6750 SŁ = 84,38 zł -> 85 zł; 750 SŁ = 9,38 zł -> 10 zł; 825 SŁ = 10,31 zł -> 11 zł.
        assertThat(UnbindCostCalculator.grosze("Legendarny", 100)).hasValue(5_700);
        assertThat(UnbindCostCalculator.grosze("Legendarny", 300)).hasValue(8_500);
        assertThat(UnbindCostCalculator.grosze("Zwykły", 1)).hasValue(1_000);
        assertThat(UnbindCostCalculator.grosze("Zwykły", 6)).hasValue(1_100);
        assertThat(UnbindCostCalculator.grosze("Ulepszony", 100)).isEmpty();
    }
}
