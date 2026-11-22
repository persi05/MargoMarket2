package com.margomarket.catalog.internal;

import com.margomarket.catalog.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemTypeRepository extends JpaRepository<ItemType, Long> {
    List<ItemType> findAllByOrderByNameAsc();
}