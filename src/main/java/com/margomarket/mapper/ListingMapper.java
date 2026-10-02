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
        return toResponse(listing, true);
    }

    public ListingResponse toResponse(Listing listing, boolean showPrivateDetails) {
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
                listing.isBound(),
                lookupMapper.toResponse(listing.getRarity()),
                showPrivateDetails ? listing.getPrice() : null,
                lookupMapper.toResponse(listing.getCurrency()),
                lookupMapper.toResponse(listing.getServer()),
                showPrivateDetails ? listing.getContact() : null,
                listing.getStatus().getName(),
                listing.getUser().getId(),
                showPrivateDetails ? listing.getUser().getEmail() : null,
                listing.getCreatedAt(),
                listing.getSoldAt()
        );
    }
}
