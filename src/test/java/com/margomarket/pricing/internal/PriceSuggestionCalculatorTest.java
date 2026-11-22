package com.margomarket.pricing.internal;

import com.margomarket.catalog.Server;
import com.margomarket.catalog.WorldType;

import static com.margomarket.pricing.internal.PriceSample.Currency.GOLD;
import static com.margomarket.pricing.internal.PriceSample.Currency.PLN;
import static com.margomarket.pricing.internal.PriceSample.Source.*;
import static com.margomarket.pricing.internal.PriceSuggestionCalculator.Basis.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

class PriceSuggestionCalculatorTest {
    private static final Instant NOW = Instant.parse("2026-11-01T12:00:00Z");
    // Legendary level 40: +3 needs 748 000 points = 74,80 zł at 100 zł per million.
    private static final long PLUS_THREE_UPGRADE = 7_480;

    private final Server classic = world(1, WorldType.TRANSFERABLE, 20_000_000L, 10_000L, 100L);
    private final Server gefion = world(2, WorldType.TRANSFERABLE, 25_000_000L, 8_000L, 100L);
    private final Server fobos = world(3, WorldType.ISOLATED, 22_000_000L, 10_000L, 100L);
    private final Server perkun = world(4, WorldType.ISOLATED, 20_000_000L, null, null);
    private final Map<Long, Server> worlds = Map.of(1L, classic, 2L, gefion, 3L, fobos, 4L, perkun);
    private final AtomicLong ids = new AtomicLong();
    private final List<PriceSample> samples = new ArrayList<>();

    private static Server world(long id, WorldType type, Long goldPerPln, Long upgrade, Long essence) {
        Server server = new Server();
        server.setId(id);
        server.setName("Świat " + id);
        server.setWorldType(type);
        server.setGoldPerPln(goldPerPln);
        server.setUpgradeMillionPriceGrosze(upgrade);
        server.setEssencePriceGrosze(essence);
        return server;
    }

    private void add(PriceSample.Source source, Server world, int enhancement, PriceSample.Currency currency, long amount) {
        add(source, world, enhancement, currency, amount, false, NOW.minus(Duration.ofDays(1)));
    }

    private void add(PriceSample.Source source, Server world, int enhancement, PriceSample.Currency currency, long amount,
                     boolean bound, Instant at) {
        samples.add(new PriceSample(ids.incrementAndGet(), source, world.getId(), enhancement, bound, currency, amount, at));
    }

    private PriceSuggestionCalculator.Level level(Server target, int enhancement) {
        return suggest(target).levels().get(enhancement);
    }

    private PriceSuggestionCalculator.Result suggest(Server target) {
        return PriceSuggestionCalculator.suggest("Legendarny", 40, target, worlds, samples, false, NOW);
    }

    @Test
    void usesDirectSalesForPlusZeroAndAddsUpgradeCostForHigherLevels() {
        add(SALE, classic, 0, PLN, 10_000);
        add(SALE, classic, 0, PLN, 12_000);
        add(AUCTION, classic, 0, PLN, 11_000);

        var plusZero = level(classic, 0);
        assertThat(plusZero.basis()).isEqualTo(DIRECT);
        assertThat(plusZero.totalGrosze()).isEqualTo(11_000);
        assertThat(plusZero.totalGold()).isEqualTo(2_200_000_000L);

        var plusThree = level(classic, 3);
        assertThat(plusThree.basis()).isEqualTo(ESTIMATED);
        assertThat(plusThree.baseGrosze()).isEqualTo(11_000);
        assertThat(plusThree.upgradeGrosze()).isEqualTo(PLUS_THREE_UPGRADE);
        assertThat(plusThree.totalGrosze()).isEqualTo(11_000 + PLUS_THREE_UPGRADE);
        assertThat(plusThree.lowData()).isFalse();
    }

    @Test
    void reducesUpgradedSalesToPlusZeroWithTheirOwnWorldRates() {
        add(SALE, classic, 3, PLN, 11_000 + PLUS_THREE_UPGRADE);
        add(SALE, classic, 3, PLN, 11_000 + PLUS_THREE_UPGRADE);
        // Gefion upgrade points are cheaper: 748 000 x 80 zł / 1m = 59,84 zł.
        add(SALE, gefion, 3, PLN, 11_000 + 5_984);

        var plusZero = level(classic, 0);
        assertThat(plusZero.basis()).isEqualTo(ESTIMATED);
        assertThat(plusZero.totalGrosze()).isEqualTo(11_000);
        assertThat(level(classic, 3).basis()).isEqualTo(DIRECT);
    }

    @Test
    void prefersDirectSalesAtTheRequestedLevel() {
        add(SALE, classic, 0, PLN, 10_000);
        add(SALE, classic, 0, PLN, 10_000);
        add(SALE, classic, 0, PLN, 10_000);
        for (int i = 0; i < 3; i++) add(SALE, classic, 5, PLN, 90_000);

        var plusFive = level(classic, 5);
        assertThat(plusFive.basis()).isEqualTo(DIRECT);
        assertThat(plusFive.totalGrosze()).isEqualTo(90_000);
        assertThat(plusFive.sampleCount()).isEqualTo(3);
    }

    @Test
    void poolsTransferableWorldsInPlnButKeepsIsolatedWorldsApart() {
        add(SALE, classic, 0, PLN, 10_000);
        add(SALE, gefion, 0, GOLD, 2_500_000_000L); // 2,5g at 25m per zł = 100 zł
        add(SALE, gefion, 0, GOLD, 2_500_000_000L);
        add(SALE, fobos, 0, PLN, 99_000);

        var classicPrice = level(classic, 0);
        assertThat(classicPrice.totalGrosze()).isEqualTo(10_000);
        assertThat(classicPrice.sampleCount()).isEqualTo(3);
        assertThat(suggest(classic).marketWorldIds()).containsExactly(1L, 2L);

        var fobosPrice = level(fobos, 0);
        assertThat(fobosPrice.totalGrosze()).isEqualTo(99_000);
        assertThat(fobosPrice.sampleCount()).isEqualTo(1);
        assertThat(fobosPrice.lowData()).isTrue();
    }

    @Test
    void ignoresOutliers() {
        for (long zl : new long[]{100, 105, 110, 115}) add(SALE, classic, 0, PLN, zl * 100);
        add(SALE, classic, 0, PLN, 1_000_000);
        assertThat(level(classic, 0).totalGrosze()).isEqualTo(10_750);
    }

    @Test
    void activeOffersCountHalf() {
        add(SALE, classic, 0, PLN, 10_000);
        add(SALE, classic, 0, PLN, 10_000);
        for (int i = 0; i < 3; i++) add(OFFER, classic, 0, PLN, 20_000);
        // Unweighted median would be 200 zł; with offers at half weight the sales win.
        assertThat(level(classic, 0).totalGrosze()).isEqualTo(10_000);
    }

    @Test
    void bindingMustMatch() {
        for (int i = 0; i < 3; i++) add(SALE, classic, 0, PLN, 10_000, true, NOW);
        var unbound = level(classic, 0);
        assertThat(unbound.basis()).isEqualTo(NONE);
        assertThat(unbound.totalGrosze()).isNull();
        assertThat(PriceSuggestionCalculator.suggest("Legendarny", 40, classic, worlds, samples, true, NOW)
                .levels().get(0).totalGrosze()).isEqualTo(10_000);
    }

    @Test
    void prefersRecentSalesButFallsBackToOlderOnes() {
        Instant old = NOW.minus(Duration.ofDays(400));
        for (int i = 0; i < 3; i++) add(SALE, classic, 0, PLN, 5_000, false, old);
        assertThat(level(classic, 0).totalGrosze()).isEqualTo(5_000);
        assertThat(suggest(classic).recentOnly()).isFalse();

        for (int i = 0; i < 3; i++) add(SALE, classic, 0, PLN, 10_000);
        assertThat(level(classic, 0).totalGrosze()).isEqualTo(10_000);
        assertThat(suggest(classic).recentOnly()).isTrue();
    }

    @Test
    void worldWithoutUpgradeRateShowsOnlyThePlusZeroPrice() {
        for (int i = 0; i < 3; i++) add(SALE, perkun, 0, PLN, 10_000);
        var plusThree = level(perkun, 3);
        assertThat(plusThree.baseGrosze()).isEqualTo(10_000);
        assertThat(plusThree.upgradeGrosze()).isNull();
        assertThat(plusThree.totalGrosze()).isNull();
        assertThat(plusThree.upgradeRateMissing()).isTrue();
        assertThat(level(perkun, 0).totalGrosze()).isEqualTo(10_000);
    }

    @Test
    void goldPricesFromWorldsWithoutAGoldRateAreSkipped() {
        Server nerthus = world(5, WorldType.ISOLATED, null, null, null);
        var withNerthus = new java.util.HashMap<>(worlds);
        withNerthus.put(5L, nerthus);
        samples.add(new PriceSample(99, SALE, 5, 0, false, GOLD, 1_000_000_000L, NOW));
        samples.add(new PriceSample(100, SALE, 5, 0, false, PLN, 4_000, NOW));
        var result = PriceSuggestionCalculator.suggest("Legendarny", 40, nerthus, withNerthus, samples, false, NOW);
        assertThat(result.levels().get(0).totalGrosze()).isEqualTo(4_000);
        assertThat(result.levels().get(0).totalGold()).isNull();
    }

    @Test
    void noSamplesMeansNoSuggestion() {
        var level = level(classic, 2);
        assertThat(level.basis()).isEqualTo(NONE);
        assertThat(level.sampleCount()).isZero();
        assertThat(level.lowData()).isTrue();
        assertThat(level.upgradeGrosze()).isNotNull();
    }

    @Test
    void itemsThatCannotBeUpgradedOnlyGetAPlusZeroPriceFromAllSamples() {
        add(SALE, classic, 0, PLN, 3_000);
        add(SALE, classic, 0, PLN, 3_000);
        add(SALE, classic, 2, PLN, 3_000); // stored before the item was locked; still a +0 price
        var result = PriceSuggestionCalculator.suggest("Legendarny", 40, classic, worlds, samples, false, false, NOW);
        assertThat(result.levels()).hasSize(1);
        var plusZero = result.levels().get(0);
        assertThat(plusZero.basis()).isEqualTo(DIRECT);
        assertThat(plusZero.sampleCount()).isEqualTo(3);
        assertThat(plusZero.totalGrosze()).isEqualTo(3_000);
        assertThat(plusZero.lowData()).isFalse();
    }

    private PriceSuggestionCalculator.Level boundLevel(boolean bound, Long unbindGrosze, int enhancement) {
        return PriceSuggestionCalculator.suggest("Legendarny", 40, classic, worlds, samples, bound, true, unbindGrosze, NOW)
                .levels().get(enhancement);
    }

    @Test
    void keepsBoundAndUnboundApartWithoutAnUnbindingPrice() {
        add(SALE, classic, 0, PLN, 20_000, false, NOW.minus(Duration.ofDays(1)));
        add(SALE, classic, 0, PLN, 22_000, false, NOW.minus(Duration.ofDays(1)));
        add(SALE, classic, 0, PLN, 21_000, false, NOW.minus(Duration.ofDays(1)));

        var bound = boundLevel(true, null, 0);
        assertThat(bound.basis()).isEqualTo(NONE);
        assertThat(bound.convertedSamples()).isZero();
    }

    @Test
    void estimatesABoundPriceFromUnboundSalesMinusTheUnbindingCost() {
        add(SALE, classic, 0, PLN, 20_000, false, NOW.minus(Duration.ofDays(1)));
        add(SALE, classic, 0, PLN, 22_000, false, NOW.minus(Duration.ofDays(1)));
        add(SALE, classic, 0, PLN, 21_000, false, NOW.minus(Duration.ofDays(1)));

        var bound = boundLevel(true, 6_000L, 0);
        assertThat(bound.basis()).isEqualTo(ESTIMATED);
        assertThat(bound.totalGrosze()).isEqualTo(15_000);
        assertThat(bound.convertedSamples()).isEqualTo(3);
        assertThat(boundLevel(false, 6_000L, 0).basis()).isEqualTo(DIRECT);
    }

    @Test
    void estimatesAnUnboundPriceFromBoundSalesPlusTheUnbindingCost() {
        add(SALE, classic, 3, PLN, 9_000 + PLUS_THREE_UPGRADE, true, NOW.minus(Duration.ofDays(1)));
        add(SALE, classic, 3, PLN, 9_000 + PLUS_THREE_UPGRADE, true, NOW.minus(Duration.ofDays(1)));
        add(SALE, classic, 0, PLN, 15_000, false, NOW.minus(Duration.ofDays(1)));

        var unbound = boundLevel(false, 6_000L, 3);
        assertThat(unbound.basis()).isEqualTo(ESTIMATED);
        assertThat(unbound.baseGrosze()).isEqualTo(15_000);
        assertThat(unbound.totalGrosze()).isEqualTo(15_000 + PLUS_THREE_UPGRADE);
        assertThat(unbound.convertedSamples()).isEqualTo(2);
    }

    @Test
    void prefersSalesInTheRequestedBoundStateWhenThereAreEnough() {
        for (int i = 0; i < 3; i++) add(SALE, classic, 0, PLN, 18_000, true, NOW.minus(Duration.ofDays(1)));
        for (int i = 0; i < 5; i++) add(SALE, classic, 0, PLN, 40_000, false, NOW.minus(Duration.ofDays(1)));

        var bound = boundLevel(true, 6_000L, 0);
        assertThat(bound.basis()).isEqualTo(DIRECT);
        assertThat(bound.totalGrosze()).isEqualTo(18_000);
    }

    @Test
    void dropsConvertedSamplesThatWouldBeWorthNothing() {
        add(SALE, classic, 0, PLN, 4_000, false, NOW.minus(Duration.ofDays(1)));

        assertThat(boundLevel(true, 6_000L, 0).basis()).isEqualTo(NONE);
    }
}
