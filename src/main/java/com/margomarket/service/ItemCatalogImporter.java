package com.margomarket.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.margomarket.model.Item;
import com.margomarket.model.ItemType;
import com.margomarket.model.Rarity;
import com.margomarket.repository.ItemRepository;
import com.margomarket.repository.ItemTypeRepository;
import com.margomarket.repository.RarityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ItemCatalogImporter implements ApplicationRunner {

    private static final int BATCH_SIZE = 500;
    private static final String CATALOG_PATH = "data/margoworld-market-items.json";

    private final ObjectMapper objectMapper;
    private final ItemRepository itemRepository;
    private final ItemTypeRepository itemTypeRepository;
    private final RarityRepository rarityRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        ClassPathResource catalog = new ClassPathResource(CATALOG_PATH);
        if (!catalog.exists()) {
            return;
        }

        Map<String, ItemType> itemTypes = itemTypeRepository.findAll().stream()
                .collect(Collectors.toMap(ItemType::getName, Function.identity()));
        Map<String, Rarity> rarities = rarityRepository.findAll().stream()
                .collect(Collectors.toMap(Rarity::getName, Function.identity()));

        try (InputStream inputStream = catalog.getInputStream()) {
            List<CatalogItem> catalogItems = objectMapper.readValue(inputStream, new TypeReference<List<CatalogItem>>() {
            });
            Map<Long, Item> existingItems = itemRepository.findAll().stream()
                    .collect(Collectors.toMap(Item::getExternalId, Function.identity()));

            itemRepository.disableMarketCatalog();
            List<Item> batch = new ArrayList<>(BATCH_SIZE);
            for (CatalogItem catalogItem : catalogItems) {
                ItemType itemType = getRequiredDictionaryValue(itemTypes, catalogItem.itemTypeName(), "item type");
                Rarity rarity = getRequiredDictionaryValue(rarities, catalogItem.rarityName(), "rarity");

                Item item = existingItems.getOrDefault(catalogItem.externalId(), new Item());
                item.setExternalId(catalogItem.externalId());
                item.setName(catalogItem.name());
                item.setIconUrl(catalogItem.iconUrl());
                item.setLevel(catalogItem.level());
                item.setItemType(itemType);
                item.setRarity(rarity);
                item.setDescription(catalogItem.description());
                item.setStats(catalogItem.stats());
                item.setSource(catalogItem.source() == null || catalogItem.source().isBlank() ? "margoworld" : catalogItem.source());
                item.setSourceUrl(catalogItem.sourceUrl());
                item.setMarketEnabled(true);
                batch.add(item);

                if (batch.size() == BATCH_SIZE) {
                    itemRepository.saveAll(batch);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                itemRepository.saveAll(batch);
            }
        }
    }

    private <T> T getRequiredDictionaryValue(Map<String, T> dictionary, String sourceName, String dictionaryName) {
        T value = dictionary.get(sourceName);
        if (value == null) {
            throw new IllegalStateException("Missing " + dictionaryName + " dictionary value: " + sourceName);
        }
        return value;
    }

    private record CatalogItem(
            Long externalId,
            String name,
            String iconUrl,
            Integer level,
            String itemTypeName,
            String rarityName,
            String description,
            String stats,
            String source,
            String sourceUrl
    ) {
    }
}
