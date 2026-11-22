package com.margomarket.catalog;

public record CatalogItem(
        Long externalId,
        String name,
        String iconUrl,
        Integer level,
        String itemTypeName,
        String rarityName,
        String description,
        String stats,
        String source,
        String sourceUrl) {}
