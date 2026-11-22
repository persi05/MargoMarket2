package com.margomarket.monitoring.internal;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * DOWN when active auctions stay open longer than {@link MarketSnapshot#CLOSING_GRACE} past their end:
 * the scheduler that settles auctions has stopped or cannot keep up. Shown as {@code auctionClosing}.
 */
@Component("auctionClosing")
class AuctionClosingHealthIndicator implements HealthIndicator {
    private final MarketSnapshot snapshot;

    AuctionClosingHealthIndicator(MarketSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public Health health() {
        MarketSnapshot.Counts counts = snapshot.refresh();
        Health.Builder health = counts.overdueAuctions() == 0 ? Health.up() : Health.down();
        health.withDetail("activeAuctions", counts.activeAuctions()).withDetail("overdueAuctions", counts.overdueAuctions());
        if (counts.oldestOverdueEnd() != null) health.withDetail("oldestOverdueEnd", counts.oldestOverdueEnd().toString());
        return health.build();
    }
}
