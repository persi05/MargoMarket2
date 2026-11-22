package com.margomarket.catalog;

import org.springframework.stereotype.Component;

@Component
public class LookupMapper {

    public LookupResponse toResponse(Server server) {
        return new LookupResponse(server.getId(), server.getName());
    }

    public LookupResponse toResponse(ItemType itemType) {
        return new LookupResponse(itemType.getId(), itemType.getName());
    }

    public LookupResponse toResponse(Rarity rarity) {
        return new LookupResponse(rarity.getId(), rarity.getName());
    }

    public LookupResponse toResponse(Currency currency) {
        return new LookupResponse(currency.getId(), currency.getName());
    }
}
