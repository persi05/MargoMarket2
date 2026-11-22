package com.margomarket.pricing.internal;

import com.margomarket.catalog.Server;
import com.margomarket.catalog.WorldType;

import java.time.Instant;

public record WorldResponse(long id, String name, WorldType worldType, Long goldPerPln, Long upgradeMillionPriceGrosze,
                            Long essencePriceGrosze, Instant ratesUpdatedAt) {
    public static WorldResponse of(Server server) {
        return new WorldResponse(server.getId(), server.getName(), server.getWorldType(), server.getGoldPerPln(),
                server.getUpgradeMillionPriceGrosze(), server.getEssencePriceGrosze(), server.getRatesUpdatedAt());
    }
}
