package com.margomarket.pricing.internal;

import com.margomarket.catalog.Server;
import com.margomarket.catalog.Catalog;
import com.margomarket.shared.NotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorldAdminService {
    private final Catalog catalog;

    @Transactional(readOnly = true)
    public List<WorldResponse> list() {
        return catalog.servers().stream().map(WorldResponse::of).toList();
    }

    @Transactional
    public WorldResponse create(WorldRatesRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.length() < 2) throw new IllegalArgumentException("Podaj nazwę świata");
        boolean exists = catalog.servers().stream().anyMatch(s -> s.getName().equalsIgnoreCase(name));
        if (exists) throw new IllegalArgumentException("Świat o tej nazwie już istnieje");
        Server server = new Server();
        server.setName(name);
        return WorldResponse.of(catalog.addServer(apply(server, request)));
    }

    @Transactional
    public WorldResponse update(long id, WorldRatesRequest request) {
        Server server = catalog.server(id).orElseThrow(() -> new NotFoundException("Nie znaleziono świata"));
        return WorldResponse.of(apply(server, request));
    }

    private static Server apply(Server server, WorldRatesRequest request) {
        server.setWorldType(request.worldType());
        server.setGoldPerPln(request.goldPerPln());
        server.setUpgradeMillionPriceGrosze(request.upgradeMillionPriceGrosze());
        server.setEssencePriceGrosze(request.essencePriceGrosze());
        server.setRatesUpdatedAt(Instant.now());
        return server;
    }
}
