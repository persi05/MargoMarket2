package com.margomarket.catalog;

public record LookupResponse(Long id, String name) {
    public static LookupResponse of(Server server) {
        return new LookupResponse(server.getId(), server.getName());
    }

    public static LookupResponse of(ItemType itemType) {
        return new LookupResponse(itemType.getId(), itemType.getName());
    }

    public static LookupResponse of(Rarity rarity) {
        return new LookupResponse(rarity.getId(), rarity.getName());
    }

    public static LookupResponse of(Currency currency) {
        return new LookupResponse(currency.getId(), currency.getName());
    }
}
