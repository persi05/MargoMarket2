package com.margomarket.pricing;


import java.time.Instant;

/**
 * One observed price of a catalog item. {@code amount} is in grosze for PLN and in gold units for GOLD.
 */
public record PriceSample(long listingId, Source source, long serverId, int enhancement, boolean bound,
                          Currency currency, long amount, Instant at) {

    public enum Source {
        /** Fixed-price listing marked as sold. */
        SALE(1.0),
        /** Auction that ended with a winner; the winning bid. */
        AUCTION(1.0),
        /** Active fixed-price listing; an asking price, so it counts half. */
        OFFER(0.5);

        private final double weight;

        Source(double weight) { this.weight = weight; }

        public double weight() { return weight; }
    }

    public enum Currency { PLN, GOLD }
}
