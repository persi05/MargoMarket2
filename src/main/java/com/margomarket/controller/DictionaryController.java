package com.margomarket.controller;

import com.margomarket.dto.LookupResponse;
import com.margomarket.mapper.LookupMapper;
import com.margomarket.repository.CurrencyRepository;
import com.margomarket.repository.ItemTypeRepository;
import com.margomarket.repository.RarityRepository;
import com.margomarket.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/dictionaries")
@RequiredArgsConstructor
public class DictionaryController {

    private static final Set<String> MARKET_ITEM_TYPES = Set.of(
            "Jednoręczne", "Dwuręczne", "Półtoraręczne", "Tarcza", "Dystansowe",
            "Pomocnicze", "Różdżki", "Orby", "Strzały", "Zbroja", "Hełm",
            "Buty", "Rękawice", "Pierścień", "Naszyjnik", "Talizmany",
            "Konsumpcyjne", "Waluta", "Torby"
    );

    private final ServerRepository serverRepository;
    private final ItemTypeRepository itemTypeRepository;
    private final RarityRepository rarityRepository;
    private final CurrencyRepository currencyRepository;
    private final LookupMapper lookupMapper;

    @GetMapping
    public Map<String, List<LookupResponse>> all() {
        return Map.of(
                "servers", serverRepository.findAllByOrderByNameAsc().stream()
                        .map(lookupMapper::toResponse)
                        .toList(),
                "itemTypes", itemTypeRepository.findAllByOrderByNameAsc().stream()
                        .filter(type -> MARKET_ITEM_TYPES.contains(type.getName()))
                        .map(lookupMapper::toResponse)
                        .toList(),
                "rarities", rarityRepository.findAllByOrderByIdAsc().stream()
                        .map(lookupMapper::toResponse)
                        .toList(),
                "currencies", currencyRepository.findAllByOrderByIdAsc().stream()
                        .map(lookupMapper::toResponse)
                        .toList()
        );
    }
}
