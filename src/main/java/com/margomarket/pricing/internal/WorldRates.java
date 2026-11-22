package com.margomarket.pricing.internal;

import com.margomarket.catalog.Server;

import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.OptionalLong;

/** Converts between gold, PLN and upgrade costs using one world's admin-set rates. All PLN values are in grosze. */
public final class WorldRates {
    private WorldRates() {}

    public static OptionalLong goldToGrosze(long gold, Server world) {
        if (world.getGoldPerPln() == null) return OptionalLong.empty();
        return OptionalLong.of(divideRounded(BigInteger.valueOf(gold).multiply(BigInteger.valueOf(100)), world.getGoldPerPln()));
    }

    public static OptionalLong groszeToGold(long grosze, Server world) {
        if (world.getGoldPerPln() == null) return OptionalLong.empty();
        return OptionalLong.of(divideRounded(BigInteger.valueOf(grosze).multiply(BigInteger.valueOf(world.getGoldPerPln())), 100));
    }

    /** Price of the given upgrade in grosze; empty when the world lacks a rate the upgrade needs. */
    public static OptionalLong upgradeGrosze(UpgradeCostCalculator.UpgradeCost cost, Server world) {
        long total = 0;
        if (cost.points() > 0) {
            if (world.getUpgradeMillionPriceGrosze() == null) return OptionalLong.empty();
            total += divideRounded(BigInteger.valueOf(cost.points()).multiply(BigInteger.valueOf(world.getUpgradeMillionPriceGrosze())), 1_000_000);
        }
        if (cost.gold() > 0) {
            OptionalLong gold = goldToGrosze(cost.gold(), world);
            if (gold.isEmpty()) return OptionalLong.empty();
            total += gold.getAsLong();
        }
        if (cost.essence() > 0) {
            if (world.getEssencePriceGrosze() == null) return OptionalLong.empty();
            total += cost.essence() * world.getEssencePriceGrosze();
        }
        return OptionalLong.of(total);
    }

    private static long divideRounded(BigInteger value, long divisor) {
        return new java.math.BigDecimal(value).divide(java.math.BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
