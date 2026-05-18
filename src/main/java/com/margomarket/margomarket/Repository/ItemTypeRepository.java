package com.margomarket.margomarket.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.margomarket.margomarket.model.ItemType;

import java.util.List;

public interface ItemTypeRepository extends JpaRepository<ItemType, Long> {
    List<ItemType> findAllByOrderByNameAsc();
}