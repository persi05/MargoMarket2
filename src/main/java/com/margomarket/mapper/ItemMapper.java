package com.margomarket.mapper;

import com.margomarket.dto.ItemResponse;
import com.margomarket.model.Item;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ItemMapper {

    private final LookupMapper lookupMapper;

    public ItemResponse toResponse(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getExternalId(),
                item.getName(),
                item.getIconUrl(),
                item.getLevel(),
                lookupMapper.toResponse(item.getItemType()),
                lookupMapper.toResponse(item.getRarity()),
                item.getDescription(),
                item.getStats()
        );
    }
}
