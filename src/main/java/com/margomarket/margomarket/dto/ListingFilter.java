package com.margomarket.margomarket.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ListingFilter {

    private String search;
    private Long serverId;
    private Long itemTypeId;
    private Long rarityId;
    private Long currencyId;
    private Integer minLevel;
    private Integer maxLevel;
    private String status;
    private Integer page = 1;

    public int getMinLevelOrDefault() {
        return (minLevel != null && minLevel >= 0) ? Math.min(minLevel, 300) : 0;
    }

    public int getMaxLevelOrDefault() {
        return (maxLevel != null && maxLevel >= 0) ? Math.min(maxLevel, 300) : 300;
    }

    public String getNullableSearch() {
        return (search != null && !search.isBlank()) ? search.trim() : null;
    }
}