package com.margomarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.margomarket.model.ItemType;

import java.util.List;

public interface ItemTypeRepository extends JpaRepository<ItemType, Long> {
    List<ItemType> findAllByOrderByNameAsc();
}