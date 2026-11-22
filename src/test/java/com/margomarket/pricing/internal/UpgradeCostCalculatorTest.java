package com.margomarket.pricing.internal;

import com.margomarket.pricing.internal.UpgradeCostCalculator.UpgradeCost;

import com.margomarket.catalog.Server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UpgradeCostCalculatorTest {
    private static UpgradeCost cost(String rarity, int level, int enhancement) {
        return UpgradeCostCalculator.cost(rarity, level, enhancement).orElseThrow();
    }

    @Test
    void matchesTheForumExampleForALegendaryItem() {
        // (180 + 40) x 1000 x 340% = 748 000
        assertThat(cost("Legendarny", 40, 3).points()).isEqualTo(748_000);
    }

    @Test
    void usesCumulativeLevelFactors() {
        long base = 1000L * (180 + 200);
        assertThat(cost("Legendarny", 200, 0)).isEqualTo(UpgradeCost.NONE);
        assertThat(cost("Legendarny", 200, 1).points()).isEqualTo(base);
        assertThat(cost("Legendarny", 200, 2).points()).isEqualTo(base * 21 / 10);
        assertThat(cost("Legendarny", 200, 4).points()).isEqualTo(base * 5);
        assertThat(cost("Legendarny", 200, 5).points()).isEqualTo(base * 7);
    }

    @Test
    void appliesRarityFactors() {
        assertThat(cost("Zwykły", 50, 1).points()).isEqualTo(230);
        assertThat(cost("Unikatowy", 50, 1).points()).isEqualTo(2_300);
        assertThat(cost("Heroiczny", 50, 1).points()).isEqualTo(23_000);
        assertThat(cost("Ulepszony", 50, 1).points()).isEqualTo(150 * 50 + 27_000);
    }

    @Test
    void plusFiveAlsoNeedsGoldAndEssence() {
        var legendary = cost("Legendarny", 200, 5);
        assertThat(legendary.gold()).isEqualTo((10L * 200 + 1300) * 200 * 60);
        assertThat(legendary.essence()).isEqualTo(3 * 30);
        assertThat(cost("Heroiczny", 200, 5).gold()).isEqualTo((10L * 200 + 1300) * 200 * 30);
        assertThat(cost("Ulepszony", 200, 5).gold()).isEqualTo((10L * 200 + 1300) * 200 * 40);
        assertThat(cost("Legendarny", 200, 4).gold()).isZero();
        assertThat(cost("Legendarny", 200, 4).essence()).isZero();
    }

    @Test
    void artefactsAndUnknownRaritiesCannotBePriced() {
        assertThat(UpgradeCostCalculator.cost("Artefakt", 100, 3)).isEmpty();
        assertThat(UpgradeCostCalculator.cost(null, 100, 3)).isEmpty();
        assertThatThrownBy(() -> UpgradeCostCalculator.cost("Legendarny", 100, 6)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertsUpgradeCostWithWorldRates() {
        var world = new Server();
        world.setGoldPerPln(20_000_000L);
        world.setUpgradeMillionPriceGrosze(10_000L);
        world.setEssencePriceGrosze(100L);

        var plusThree = cost("Legendarny", 40, 3); // 748 000 points x 100 zł / 1m = 74,80 zł
        assertThat(WorldRates.upgradeGrosze(plusThree, world)).hasValue(7_480);

        var plusFive = cost("Legendarny", 200, 5);
        long points = 2_660_000L * 10_000 / 1_000_000;             // 266 zł
        long gold = 39_600_000L * 100 / 20_000_000;                  // 39,6m gold -> 1,98 zł
        long essence = 90L * 100;                                     // 90 x 1 zł
        assertThat(WorldRates.upgradeGrosze(plusFive, world)).hasValue(points + gold + essence);
    }

    @Test
    void missingRatesMakeTheUpgradeUnpriceable() {
        var world = new Server();
        world.setGoldPerPln(20_000_000L);
        assertThat(WorldRates.upgradeGrosze(cost("Legendarny", 40, 1), world)).isEmpty();
        assertThat(WorldRates.upgradeGrosze(UpgradeCost.NONE, world)).hasValue(0);
    }

    @Test
    void convertsGoldAndPln() {
        var world = new Server();
        world.setGoldPerPln(18_000_000L);
        assertThat(WorldRates.goldToGrosze(1_800_000_000L, world)).hasValue(10_000);
        assertThat(WorldRates.groszeToGold(10_000, world)).hasValue(1_800_000_000L);
        assertThat(WorldRates.goldToGrosze(1, new Server())).isEmpty();
    }
}
