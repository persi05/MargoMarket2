package com.margomarket.dto;

import com.margomarket.model.Server;
import com.margomarket.model.WorldType;

import java.time.Instant;

public record WorldResponse(long id, String name, WorldType worldType, Long goldPerPln, Long upgradeMillionPriceGrosze,
                            Long essencePriceGrosze, Instant ratesUpdatedAt) {
    public static WorldResponse of(Server server) {
        return new WorldResponse(server.getId(), server.getName(), server.getWorldType(), server.getGoldPerPln(),
                server.getUpgradeMillionPriceGrosze(), server.getEssencePriceGrosze(), server.getRatesUpdatedAt());
    }
}
