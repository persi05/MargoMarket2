package com.margomarket.mapper;

import com.margomarket.dto.ListingResponse;
import com.margomarket.model.Listing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListingMapper {

    private final LookupMapper lookupMapper;

    public ListingResponse toResponse(Listing listing) {
        return new ListingResponse(
                listing.getId(),
                listing.getItem() == null ? null : listing.getItem().getId(),
                listing.getItemName(),
                listing.getItem() == null ? null : listing.getItem().getIconUrl(),
                listing.getItem() == null ? null : listing.getItem().getDescription(),
                listing.getItem() == null ? null : listing.getItem().getStats(),
                lookupMapper.toResponse(listing.getItemType()),
                listing.getLevel(),
                listing.getEnhancementLevel(),
                lookupMapper.toResponse(listing.getRarity()),
                listing.getPrice(),
                lookupMapper.toResponse(listing.getCurrency()),
                lookupMapper.toResponse(listing.getServer()),
                listing.getContact(),
                listing.getStatus().getName(),
                listing.getUser().getId(),
                listing.getUser().getEmail(),
                listing.getCreatedAt(),
                listing.getSoldAt()
        );
    }
}
