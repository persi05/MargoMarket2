package com.margomarket.monitoring.internal;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.util.function.ToLongFunction;

/**
 * Business gauges exported at /actuator/metrics and /actuator/prometheus
 * (e.g. {@code margomarket_auctions_active}).
 */
@Component
class MarketMetrics implements MeterBinder {
    private final MarketSnapshot snapshot;

    MarketMetrics(MarketSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        gauge(registry, "margomarket.auctions.active", "Trwające aukcje", MarketSnapshot.Counts::activeAuctions);
        gauge(registry, "margomarket.auctions.overdue",
                "Aktywne aukcje, które powinny być już zamknięte (opóźnienie harmonogramu)", MarketSnapshot.Counts::overdueAuctions);
        Gauge.builder("margomarket.listings.active", () -> snapshot.counts().activeItemListings())
                .description("Aktywne ogłoszenia z ceną stałą").tag("kind", "item").register(registry);
        Gauge.builder("margomarket.listings.active", () -> snapshot.counts().activeGoldListings())
                .description("Aktywne ogłoszenia z ceną stałą").tag("kind", "gold").register(registry);
        gauge(registry, "margomarket.conversations.open", "Otwarte rozmowy", MarketSnapshot.Counts::openConversations);
        Gauge.builder("margomarket.reports.open", () -> snapshot.counts().openConversationReports())
                .description("Zgłoszenia czekające na moderatora").tag("type", "conversation").register(registry);
        Gauge.builder("margomarket.reports.open", () -> snapshot.counts().openAuctionReports())
                .description("Zgłoszenia czekające na moderatora").tag("type", "auction").register(registry);
        gauge(registry, "margomarket.fulfillments.disputed", "Sporne rozliczenia aukcji", MarketSnapshot.Counts::disputedFulfillments);
    }

    private void gauge(MeterRegistry registry, String name, String description, ToLongFunction<MarketSnapshot.Counts> value) {
        Gauge.builder(name, () -> value.applyAsLong(snapshot.counts())).description(description).register(registry);
    }
}
