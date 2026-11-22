package com.margomarket.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** How many listings each user has, by status, for the admin user list. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserListingStats {
    private final ListingRepository listingRepository;

    public UserStats forUser(Long userId) {
        Object[] counts = listingRepository.countUserListingsByStatus(userId).get(0);
        return new UserStats(
                ((Number) counts[0]).longValue(),
                ((Number) counts[1]).longValue(),
                ((Number) counts[2]).longValue()
        );
    }

    public Map<Long, UserStats> forUsers(List<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return listingRepository.countUserListingsByStatusForUsers(userIds).stream()
                .collect(Collectors.toMap(
                        counts -> ((Number) counts[0]).longValue(),
                        counts -> new UserStats(
                                ((Number) counts[1]).longValue(),
                                ((Number) counts[2]).longValue(),
                                ((Number) counts[3]).longValue()
                        )
                ));
    }
}
