package com.margomarket.repository;

import com.margomarket.model.BlockedWord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BlockedWordRepository extends JpaRepository<BlockedWord, Long> {
    List<BlockedWord> findAllByOrderByTermAsc();
    List<BlockedWord> findByEnabledTrueOrderByTermAsc();
    boolean existsByTermAndIdNot(String term, Long id);
}
