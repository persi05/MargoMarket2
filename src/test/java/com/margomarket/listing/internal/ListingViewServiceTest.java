package com.margomarket.listing.internal;

import com.margomarket.listing.AuctionSummary;
import com.margomarket.listing.Listing;
import com.margomarket.listing.ListingResponse;
import com.margomarket.listing.auction.AuctionRepository;
import com.margomarket.shared.PageMapper;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListingViewServiceTest {
    private final AuctionRepository auctions = mock(AuctionRepository.class);
    private final ListingMapper mapper = mock(ListingMapper.class);
    private final ListingViewService views = new ListingViewService(auctions, mapper, new PageMapper());

    @Test void loadsAuctionCardDetailsInOneBatchAlongsideFixedListings() {
        Listing first = listing(10L, "AUCTION");
        Listing fixed = listing(11L, "FIXED");
        Listing second = listing(12L, "AUCTION");
        Instant end = Instant.parse("2026-11-21T12:00:00Z");
        when(auctions.getAll(List.of(10L, 12L))).thenReturn(List.of(
                new AuctionRepository.State(10L, "GOLD", 500000000L, end, end, end.minusSeconds(3600),
                        "ACTIVE", 1, 20L, 750000000L, 7L, 3),
                new AuctionRepository.State(12L, "PLN", 1000L, end, end, end.minusSeconds(3600),
                        "ACTIVE", 0, null, null, null, 0)));
        var response = mock(ListingResponse.class);
        when(mapper.toResponse(any(), eq(true), nullable(AuctionSummary.class))).thenReturn(response);

        var page = views.page(new PageImpl<>(List.of(first, fixed, second)), true);

        assertThat(page.content()).hasSize(3);
        verify(auctions).getAll(List.of(10L, 12L));
        var summaries = ArgumentCaptor.forClass(AuctionSummary.class);
        verify(mapper, times(3)).toResponse(any(), eq(true), summaries.capture());
        assertThat(summaries.getAllValues().get(0).highestAmount()).isEqualTo(750000000L);
        assertThat(summaries.getAllValues().get(1)).isNull();
        assertThat(summaries.getAllValues().get(2).startingAmount()).isEqualTo(1000L);
    }

    @Test void fixedListingsDoNotTriggerAuctionQueries() {
        when(mapper.toResponse(any(), eq(true), isNull())).thenReturn(mock(ListingResponse.class));
        views.page(new PageImpl<>(List.of(listing(11L, "FIXED"))), true);
        verifyNoInteractions(auctions);
    }

    private Listing listing(long id, String mode) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setSaleMode(mode);
        return listing;
    }
}
