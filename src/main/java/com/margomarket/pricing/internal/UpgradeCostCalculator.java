package com.margomarket.pricing.internal;

import com.margomarket.catalog.Rarity;

import java.util.Optional;

/**
 * Item upgrade costs from the official game formulas (pomoc.margonem.pl, "System ulepszania przedmiotów").
 * Points to reach +N: rarity factor x level factor(N) x (180 + item level); "Ulepszony" items use
 * level factor(N) x (150 x level + 27000). Reaching +5 also costs gold and essence.
 */
public final class UpgradeCostCalculator {
    public static final int MAX_ENHANCEMENT = 5;

    /** Cumulative level factors x10 for +0..+5: 0, 1.0, 2.1, 3.4, 5.0, 7.0. */
    private static final int[] LEVEL_FACTOR_TENTHS = {0, 10, 21, 34, 50, 70};

    public record UpgradeCost(long points, long gold, long essence) {
        public static final UpgradeCost NONE = new UpgradeCost(0, 0, 0);
    }

    private record Rarity(long pointsFactor, long goldFactor, boolean upgradedFormula) {}

    private UpgradeCostCalculator() {}

    /** Total cost of upgrading an item from +0 to +{@code enhancement}; empty when the rarity cannot be upgraded. */
    public static Optional<UpgradeCost> cost(String rarityName, int level, int enhancement) {
        if (enhancement < 0 || enhancement > MAX_ENHANCEMENT)
            throw new IllegalArgumentException("Poziom ulepszenia musi wynosić od 0 do 5");
        Rarity rarity = rarity(rarityName);
        if (rarity == null) return Optional.empty();
        if (enhancement == 0) return Optional.of(UpgradeCost.NONE);
        int lvl = Math.max(1, level);
        long base = rarity.upgradedFormula() ? 150L * lvl + 27_000 : rarity.pointsFactor() * (180L + lvl);
        long points = base * LEVEL_FACTOR_TENTHS[enhancement] / 10;
        if (enhancement < MAX_ENHANCEMENT) return Optional.of(new UpgradeCost(points, 0, 0));
        long gold = (10L * lvl + 1300) * lvl * rarity.goldFactor();
        long essence = 3 * Math.round(lvl / 10.0 + 10);
        return Optional.of(new UpgradeCost(points, gold, essence));
    }

    private static Rarity rarity(String name) {
        if (name == null) return null;
        return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "zwykły" -> new Rarity(1, 1, false);
            case "unikatowy" -> new Rarity(10, 10, false);
            case "heroiczny" -> new Rarity(100, 30, false);
            case "ulepszony" -> new Rarity(0, 40, true);
            case "legendarny" -> new Rarity(1000, 60, false);
            default -> null;
        };
    }
}
