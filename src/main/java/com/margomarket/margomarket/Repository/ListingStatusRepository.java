package com.margomarket.margomarket.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.margomarket.margomarket.model.ListingStatus;

import java.util.Optional;

public interface ListingStatusRepository extends JpaRepository<ListingStatus, Long> {
    Optional<ListingStatus> findByName(String name);
}