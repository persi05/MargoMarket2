package com.margomarket.catalog;

import com.margomarket.catalog.internal.CurrencyRepository;
import com.margomarket.catalog.internal.ItemRepository;
import com.margomarket.catalog.internal.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Items, worlds and currencies for other modules. The catalog repositories stay behind this class. */
@Service
@RequiredArgsConstructor
public class Catalog {
    private final ItemRepository items;
    private final ServerRepository servers;
    private final CurrencyRepository currencies;

    public Optional<Item> item(long id) {
        return items.findById(id);
    }

    public Optional<Server> server(long id) {
        return servers.findById(id);
    }

    /** All worlds, sorted by name. */
    public List<Server> servers() {
        return servers.findAllByOrderByNameAsc();
    }

    public Server addServer(Server server) {
        return servers.save(server);
    }

    public Optional<Currency> currency(long id) {
        return currencies.findById(id);
    }

    public Optional<Currency> currencyNamed(String name) {
        return currencies.findByNameIgnoreCase(name);
    }
}
