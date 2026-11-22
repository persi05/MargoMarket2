package com.margomarket.catalog.internal;

import com.margomarket.catalog.Currency;
import com.margomarket.catalog.ItemType;
import com.margomarket.catalog.LookupResponse;
import com.margomarket.catalog.Rarity;
import com.margomarket.catalog.Server;
import org.springframework.stereotype.Component;

@Component
public class LookupMapper {

    public LookupResponse toResponse(Server server) {
        return LookupResponse.of(server);
    }

    public LookupResponse toResponse(ItemType itemType) {
        return LookupResponse.of(itemType);
    }

    public LookupResponse toResponse(Rarity rarity) {
        return LookupResponse.of(rarity);
    }

    public LookupResponse toResponse(Currency currency) {
        return LookupResponse.of(currency);
    }
}
