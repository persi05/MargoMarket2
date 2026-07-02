package com.margomarket.dto;

public record ItemResponse(
        Long id,
        Long externalId,
        String name,
        String iconUrl,
        Integer level,
        LookupResponse itemType,
        LookupResponse rarity,
        String description,
        String stats
) {
}
