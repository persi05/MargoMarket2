package com.margomarket.repository;

import com.margomarket.model.BlockedWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BlockedWordRepository extends JpaRepository<BlockedWord, Long> {
    List<BlockedWord> findAllByOrderByTermAsc();

    @Query(value = """
        SELECT * FROM blocked_words
        WHERE translate(lower(term), 'ąćęłńóśźż', 'acelnoszz') LIKE concat('%', :search, '%')
        ORDER BY term ASC, id ASC
        """, countQuery = """
        SELECT count(*) FROM blocked_words
        WHERE translate(lower(term), 'ąćęłńóśźż', 'acelnoszz') LIKE concat('%', :search, '%')
        """, nativeQuery = true)
    Page<BlockedWord> searchPage(@Param("search") String search, Pageable pageable);
    List<BlockedWord> findByEnabledTrueOrderByTermAsc();
    boolean existsByTermAndIdNot(String term, Long id);
}
