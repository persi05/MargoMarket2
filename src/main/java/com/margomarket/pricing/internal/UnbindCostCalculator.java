package com.margomarket.pricing.internal;

import java.util.Locale;
import java.util.OptionalLong;

/**
 * Dragon scale (SŁ) cost of unbinding an item, from the game's support page:
 * 750 SŁ + 75 SŁ per 10 item levels, at most 2250 SŁ, then +20% for unique, +50% for heroic and +200% for legendary
 * items, rounded to the nearest 75 SŁ (in game a level 22-24 legendary costs 2775 SŁ: 2745-2790 -> 2775).
 * Dragon scales have a fixed price of 80 SŁ per 1 zł; the cost is rounded up to whole złote (you cannot buy part of a pack).
 */
public final class UnbindCostCalculator {
    /** Base cost cap before the rarity surcharge. */
    public static final long MAX_BASE_SCALES = 2250;
    /** Dragon scales bought for 1 zł. */
    public static final long SCALES_PER_PLN = 80;

    private UnbindCostCalculator() {}

    /** Scales needed to unbind the item; empty for a rarity without a known surcharge. */
    public static OptionalLong scales(String rarityName, int level) {
        Integer percent = percent(rarityName);
        if (percent == null) return OptionalLong.empty();
        long baseTenths = Math.min(7500 + 75L * Math.max(1, level), MAX_BASE_SCALES * 10);
        // baseTenths x percent is in thousandths of a scale; 75 000 of them make one 75 SŁ step.
        return OptionalLong.of(Math.round(baseTenths * percent / 75_000.0) * 75);
    }

    /** Unbinding cost in grosze at the fixed scale price, rounded up to whole złote (40,01 zł -> 41 zł). */
    public static OptionalLong grosze(String rarityName, int level) {
        OptionalLong scales = scales(rarityName, level);
        return scales.isPresent() ? OptionalLong.of(Math.ceilDiv(scales.getAsLong(), SCALES_PER_PLN) * 100) : OptionalLong.empty();
    }

    private static Integer percent(String name) {
        if (name == null) return null;
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "zwykły" -> 100;
            case "unikatowy" -> 120;
            case "heroiczny" -> 150;
            case "legendarny" -> 300;
            default -> null;
        };
    }
}
