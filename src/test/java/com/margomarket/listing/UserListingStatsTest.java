package com.margomarket.listing;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserListingStatsTest {
    private final ListingRepository listingRepository = mock(ListingRepository.class);
    private final UserListingStats stats = new UserListingStats(listingRepository);

    @Test
    void countsTotalActiveAndSoldListings() {
        when(listingRepository.countUserListingsByStatus(1L))
                .thenReturn(Collections.singletonList(new Object[] {3L, 1L, 1L}));

        UserStats result = stats.forUser(1L);

        assertThat(result.getTotalListings()).isEqualTo(3);
        assertThat(result.getActiveListings()).isEqualTo(1);
        assertThat(result.getSoldListings()).isEqualTo(1);
    }

    @Test
    void usesOneGroupedQueryForAPageOfUsers() {
        List<Long> userIds = List.of(1L, 2L, 3L);
        when(listingRepository.countUserListingsByStatusForUsers(userIds))
                .thenReturn(List.<Object[]>of(
                        new Object[] {1L, 4L, 2L, 1L},
                        new Object[] {3L, 1L, 1L, 0L}
                ));

        var result = stats.forUsers(userIds);

        assertThat(result).containsOnlyKeys(1L, 3L);
        assertThat(result.get(1L).getTotalListings()).isEqualTo(4);
        assertThat(result.get(1L).getActiveListings()).isEqualTo(2);
        assertThat(result.get(3L).getSoldListings()).isZero();
        verify(listingRepository).countUserListingsByStatusForUsers(userIds);
    }

    @Test
    void returnsNothingForAnEmptyPage() {
        assertThat(stats.forUsers(List.of())).isEmpty();
    }
}
