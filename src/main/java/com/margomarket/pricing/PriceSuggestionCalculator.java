package com.margomarket.pricing;

import com.margomarket.model.Server;
import com.margomarket.model.WorldType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Suggests prices for +0..+5 of one item on one world from observed samples. Pure logic, no I/O.
 *
 * <p>Samples come from the target world's market (all transferable worlds together, otherwise the world alone),
 * with the same bound flag, converted to PLN with each sample's own world rates. If at least {@link #MIN_SAMPLES}
 * samples exist at exactly +N, the suggestion is their weighted median. Otherwise every sample is reduced to +0 by
 * subtracting its upgrade cost, the weighted median of those +0 prices is taken and the target world's upgrade cost
 * of +N is added back.
 */
public final class PriceSuggestionCalculator {
    public static final int MIN_SAMPLES = 3;
    public static final Duration RECENT_WINDOW = Duration.ofDays(180);

    public enum Basis { DIRECT, ESTIMATED, NONE }

    public record Level(int enhancement, Basis basis, Long baseGrosze, Long upgradeGrosze, Long totalGrosze,
                        Long totalGold, int sampleCount, boolean lowData, boolean upgradeRateMissing) {}

    public record Result(List<Level> levels, boolean recentOnly, List<Long> marketWorldIds) {}

    private record Priced(PriceSample sample, long grosze) {}

    private PriceSuggestionCalculator() {}

    public static Result suggest(String rarity, int itemLevel, Server target, Map<Long, Server> worlds,
                                 List<PriceSample> samples, boolean bound, Instant now) {
        List<Long> market = marketWorldIds(target, worlds);
        List<Priced> priced = new ArrayList<>();
        for (PriceSample sample : samples) {
            if (sample.bound() != bound || !market.contains(sample.serverId())) continue;
            Server world = worlds.get(sample.serverId());
            OptionalLong grosze = sample.currency() == PriceSample.Currency.PLN
                    ? OptionalLong.of(sample.amount()) : WorldRates.goldToGrosze(sample.amount(), world);
            if (grosze.isPresent() && grosze.getAsLong() > 0) priced.add(new Priced(sample, grosze.getAsLong()));
        }
        List<Priced> recent = priced.stream().filter(p -> !p.sample().at().isBefore(now.minus(RECENT_WINDOW))).toList();
        boolean recentOnly = recent.size() >= MIN_SAMPLES;
        List<Priced> used = recentOnly ? recent : priced;

        List<Level> levels = new ArrayList<>();
        for (int n = 0; n <= UpgradeCostCalculator.MAX_ENHANCEMENT; n++)
            levels.add(level(n, rarity, itemLevel, target, worlds, used));
        return new Result(levels, recentOnly, market);
    }

    private static Level level(int n, String rarity, int itemLevel, Server target, Map<Long, Server> worlds, List<Priced> samples) {
        OptionalLong upgrade = UpgradeCostCalculator.cost(rarity, itemLevel, n)
                .map(cost -> WorldRates.upgradeGrosze(cost, target)).orElse(OptionalLong.empty());
        Long upgradeGrosze = upgrade.isPresent() ? upgrade.getAsLong() : null;

        List<Weighted> direct = samples.stream().filter(p -> p.sample().enhancement() == n)
                .map(p -> new Weighted(p.grosze(), p.sample().source().weight())).toList();
        if (direct.size() >= MIN_SAMPLES) {
            long total = weightedMedian(trimOutliers(direct));
            return new Level(n, Basis.DIRECT, null, upgradeGrosze, total, gold(total, target), direct.size(), false, false);
        }

        List<Weighted> base = new ArrayList<>();
        for (Priced p : samples) {
            Server world = worlds.get(p.sample().serverId());
            OptionalLong cost = UpgradeCostCalculator.cost(rarity, itemLevel, p.sample().enhancement())
                    .map(c -> WorldRates.upgradeGrosze(c, world)).orElse(OptionalLong.empty());
            if (cost.isEmpty()) continue;
            long plusZero = p.grosze() - cost.getAsLong();
            if (plusZero > 0) base.add(new Weighted(plusZero, p.sample().source().weight()));
        }
        if (base.isEmpty()) {
            // Fall back to the few direct samples rather than showing nothing.
            if (direct.isEmpty()) return new Level(n, Basis.NONE, null, upgradeGrosze, null, null, 0, true, upgradeGrosze == null);
            long total = weightedMedian(direct);
            return new Level(n, Basis.DIRECT, null, upgradeGrosze, total, gold(total, target), direct.size(), true, false);
        }
        long plusZero = weightedMedian(trimOutliers(base));
        Long total = upgradeGrosze == null ? null : plusZero + upgradeGrosze;
        return new Level(n, Basis.ESTIMATED, plusZero, upgradeGrosze, total, total == null ? null : gold(total, target),
                base.size(), base.size() < MIN_SAMPLES, upgradeGrosze == null);
    }

    public static List<Long> marketWorldIds(Server target, Map<Long, Server> worlds) {
        if (target.getWorldType() != WorldType.TRANSFERABLE) return List.of(target.getId());
        return worlds.values().stream().filter(w -> w.getWorldType() == WorldType.TRANSFERABLE)
                .map(Server::getId).sorted().toList();
    }

    private static Long gold(long grosze, Server target) {
        OptionalLong gold = WorldRates.groszeToGold(grosze, target);
        return gold.isPresent() ? gold.getAsLong() : null;
    }

    record Weighted(long value, double weight) {}

    /** Drops values outside 1.5 x IQR once there are enough samples to tell. */
    static List<Weighted> trimOutliers(List<Weighted> values) {
        if (values.size() < 4) return values;
        double[] sorted = values.stream().mapToDouble(Weighted::value).sorted().toArray();
        double q1 = quantile(sorted, 0.25), q3 = quantile(sorted, 0.75), spread = 1.5 * (q3 - q1);
        return values.stream().filter(w -> w.value() >= q1 - spread && w.value() <= q3 + spread).toList();
    }

    static long weightedMedian(List<Weighted> values) {
        List<Weighted> sorted = values.stream().sorted(Comparator.comparingLong(Weighted::value)).toList();
        double half = sorted.stream().mapToDouble(Weighted::weight).sum() / 2;
        double cumulative = 0;
        for (int i = 0; i < sorted.size(); i++) {
            cumulative += sorted.get(i).weight();
            if (Math.abs(cumulative - half) < 1e-9 && i + 1 < sorted.size())
                return Math.round((sorted.get(i).value() + sorted.get(i + 1).value()) / 2.0);
            if (cumulative > half) return sorted.get(i).value();
        }
        return sorted.getLast().value();
    }

    private static double quantile(double[] sorted, double q) {
        double position = q * (sorted.length - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower);
    }
}
