package com.margomarket.pricing;

import com.margomarket.dto.WorldRatesRequest;
import com.margomarket.dto.WorldResponse;
import com.margomarket.exception.NotFoundException;
import com.margomarket.model.Server;
import com.margomarket.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorldAdminService {
    private final ServerRepository servers;

    @Transactional(readOnly = true)
    public List<WorldResponse> list() {
        return servers.findAllByOrderByNameAsc().stream().map(WorldResponse::of).toList();
    }

    @Transactional
    public WorldResponse create(WorldRatesRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.length() < 2) throw new IllegalArgumentException("Podaj nazwę świata");
        boolean exists = servers.findAll().stream().anyMatch(s -> s.getName().equalsIgnoreCase(name));
        if (exists) throw new IllegalArgumentException("Świat o tej nazwie już istnieje");
        Server server = new Server();
        server.setName(name);
        return WorldResponse.of(servers.save(apply(server, request)));
    }

    @Transactional
    public WorldResponse update(long id, WorldRatesRequest request) {
        Server server = servers.findById(id).orElseThrow(() -> new NotFoundException("Nie znaleziono świata"));
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
