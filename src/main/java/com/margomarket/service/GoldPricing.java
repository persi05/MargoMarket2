package com.margomarket.service;

import java.math.BigInteger;

public final class GoldPricing {

    private GoldPricing() {}

    public static long total(long amount, String mode, long goldUnits, long priceMinor) {
        if (
            amount < 1 ||
            amount > 9_000_000_000_000_000L ||
            goldUnits < 1 ||
            goldUnits > 9_000_000_000_000_000L ||
            priceMinor < 1 ||
            priceMinor > 100_000_000L
        ) throw new IllegalArgumentException("Nieprawidłowa ilość lub kurs");
        if (
            !(
                ("M_PER_PLN".equals(mode) && priceMinor == 100) ||
                ("PLN_PER_G".equals(mode) && goldUnits == 1_000_000_000L)
            )
        ) throw new IllegalArgumentException(
            "Kurs musi określać ilość złota za 1 PLN albo cenę 1g"
        );
        BigInteger divisor = BigInteger.valueOf(goldUnits);
        BigInteger total = BigInteger.valueOf(amount)
            .multiply(BigInteger.valueOf(priceMinor))
            .multiply(BigInteger.TWO)
            .add(divisor)
            .divide(divisor.multiply(BigInteger.TWO));
        if (
            total.signum() <= 0 || total.compareTo(BigInteger.valueOf(100_000_000L)) > 0
        ) throw new IllegalArgumentException("Cena pakietu musi wynosić od 0,01 do 1 000 000 PLN");
        return total.longValueExact();
    }
}
