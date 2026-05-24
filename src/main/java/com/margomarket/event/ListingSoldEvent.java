package com.margomarket.event;

import java.util.List;

public record ListingSoldEvent(
        Long listingId,
        Long ownerId,
        List<Long> observerIds,
        String itemName
) {
}
