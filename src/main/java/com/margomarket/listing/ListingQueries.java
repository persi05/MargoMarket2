package com.margomarket.listing;

import com.margomarket.listing.internal.ListingMapper;
import com.margomarket.listing.internal.ListingRepository;
import com.margomarket.shared.PageMapper;
import com.margomarket.shared.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Listings as other modules see them: ready responses and row locks, never the repository itself. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingQueries {
    private static final int PROFILE_PAGE_SIZE = 20;

    private final ListingRepository listings;
    private final ListingService service;
    private final ListingMapper mapper;
    private final PageMapper pages;

    /** All listings for the admin panel, with private details. */
    public PageResponse<ListingResponse> adminSearch(ListingFilter filter) {
        return pages.toResponse(service.searchAllListingsAdmin(filter), mapper::toResponse);
    }

    /** Active listings shown on a public profile. */
    public PageResponse<ListingResponse> publicActive(long userId, int page, boolean showPrivateDetails) {
        return pages.toResponse(listings.findPublicActive(userId, PageRequest.of(Math.max(0, page - 1), PROFILE_PAGE_SIZE)),
                listing -> mapper.toResponse(listing, showPrivateDetails));
    }

    /** Locks the listing row until the caller's transaction ends. */
    @Transactional
    public Optional<Listing> lock(long id) {
        return listings.findLockedById(id);
    }
}
