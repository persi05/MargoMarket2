package com.margomarket.service;

import com.margomarket.dto.AuctionSummary;
import com.margomarket.dto.ListingResponse;
import com.margomarket.dto.PageResponse;
import com.margomarket.mapper.ListingMapper;
import com.margomarket.mapper.PageMapper;
import com.margomarket.model.Listing;
import com.margomarket.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListingViewService {
    private final AuctionRepository auctions;
    private final ListingMapper mapper;
    private final PageMapper pageMapper;

    @Transactional(readOnly = true)
    public PageResponse<ListingResponse> page(Page<Listing> listings, boolean showPrivateDetails) {
        var auctionIds = listings.getContent().stream().filter(l -> "AUCTION".equals(l.getSaleMode()))
                .map(Listing::getId).toList();
        Map<Long, AuctionSummary> summaries = auctionIds.isEmpty() ? Map.of() : auctions.getAll(auctionIds).stream()
                .collect(Collectors.toMap(AuctionRepository.State::id, a -> new AuctionSummary(
                        a.currency(), a.startingAmount(), a.highestAmount(), a.bidCount(), a.endsAt(), a.status())));
        return pageMapper.toResponse(listings, l -> mapper.toResponse(l, showPrivateDetails, summaries.get(l.getId())));
    }
}
