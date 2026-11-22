package com.margomarket.listing;

import java.util.List;

/** Published when a listing is marked as sold; notifications go out after the commit, through the outbox. */
public record ListingSoldEvent(
        Long listingId,
        Long ownerId,
        List<Long> observerIds,
        String itemName
) {
}
