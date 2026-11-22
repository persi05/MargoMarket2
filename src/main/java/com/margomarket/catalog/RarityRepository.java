package com.margomarket.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RarityRepository extends JpaRepository<Rarity, Long> {
    List<Rarity> findAllByOrderByIdAsc();
}