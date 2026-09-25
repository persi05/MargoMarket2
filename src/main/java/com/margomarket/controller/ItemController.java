package com.margomarket.controller;

import com.margomarket.dto.ItemResponse;
import com.margomarket.mapper.ItemMapper;
import com.margomarket.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ItemController {

    private static final int DEFAULT_RESULTS = 60;
    private static final int MAX_RESULTS = 75;

    private final ItemRepository itemRepository;
    private final ItemMapper itemMapper;

    @GetMapping("/api/items")
    public List<ItemResponse> search(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) Long itemTypeId,
            @RequestParam(required = false) Integer minLevel,
            @RequestParam(required = false) Integer maxLevel,
            @RequestParam(defaultValue = "" + DEFAULT_RESULTS) int limit
    ) {
        String normalizedSearch = search.trim();
        int pageSize = Math.min(Math.max(1, limit), MAX_RESULTS);
        Integer normalizedMinLevel = minLevel == null || minLevel < 1 ? null : Math.min(minLevel, 300);
        Integer normalizedMaxLevel = maxLevel == null || maxLevel < 1 ? null : Math.min(maxLevel, 300);
        Long normalizedItemTypeId = itemTypeId == null || itemTypeId < 1 ? null : itemTypeId;

        return itemRepository.searchCatalog(
                        normalizedSearch,
                        normalizedItemTypeId,
                        normalizedMinLevel,
                        normalizedMaxLevel,
                        PageRequest.of(0, pageSize)
                ).stream()
                .map(itemMapper::toResponse)
                .toList();
    }
}
