package com.margomarket.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ItemCatalogImporter implements ApplicationRunner {
    private final ObjectMapper objectMapper;
    private final CatalogStore store;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (store.initialized()) return;
        var catalog = new ClassPathResource("data/margoworld-market-items.json");
        if (!catalog.exists()) return;
        try (var input = catalog.getInputStream()) {
            store.initialize(
                    objectMapper.readValue(input, new TypeReference<List<CatalogItem>>() {}));
        }
    }
}
