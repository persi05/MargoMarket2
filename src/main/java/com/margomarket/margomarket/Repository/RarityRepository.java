package com.margomarket.margomarket.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.margomarket.margomarket.model.Rarity;

import java.util.List;

public interface RarityRepository extends JpaRepository<Rarity, Long> {
    List<Rarity> findAllByOrderByIdAsc();
}