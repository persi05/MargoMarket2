package com.margomarket.service;

import java.time.Duration;
import java.time.Instant;

public final class AuctionRules {
    private AuctionRules() {}

    public static long limit(String currency) {
        return switch (currency) {
            case "PLN" -> 100_000_000L;
            case "GOLD" -> 9_000_000_000_000_000L;
            default -> throw new IllegalArgumentException("Wybierz PLN lub złoto");
        };
    }

    public static void amount(String currency, long amount) {
        if (amount < 1 || amount > limit(currency)) throw new IllegalArgumentException("Kwota poza dozwolonym zakresem");
    }

    public static Long next(String currency, long current) {
        long result = current + Math.max("PLN".equals(currency) ? 100L : 1_000_000L, (current + 99) / 100);
        return result <= limit(currency) ? result : null;
    }

    public static void duration(Instant now, Instant end) {
        if (end == null || end.isBefore(now.plus(Duration.ofHours(1))) || end.isAfter(now.plus(Duration.ofDays(7))))
            throw new IllegalArgumentException("Aukcja musi trwać od 1 godziny do 7 dni");
    }

    public static Instant extendedEnd(Instant now, Instant end) {
        return now.plusSeconds(120).isAfter(end) ? now.plusSeconds(120) : end;
    }
}
