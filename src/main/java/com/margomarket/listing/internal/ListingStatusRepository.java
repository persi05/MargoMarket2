package com.margomarket.listing.internal;

import com.margomarket.listing.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ListingStatusRepository extends JpaRepository<ListingStatus, Long> {
    Optional<ListingStatus> findByName(String name);
}