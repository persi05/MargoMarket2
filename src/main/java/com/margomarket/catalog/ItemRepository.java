package com.margomarket.catalog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    @EntityGraph(attributePaths = {"itemType", "rarity"})
    @Query("""
        SELECT i
        FROM Item i
        WHERE i.marketEnabled = true
          AND (:search = '' OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:itemTypeId IS NULL OR i.itemType.id = :itemTypeId)
          AND (:minLevel IS NULL OR i.level >= :minLevel)
          AND (:maxLevel IS NULL OR i.level <= :maxLevel)
        ORDER BY
          CASE
            WHEN :search = '' THEN 2
            WHEN LOWER(i.name) = LOWER(:search) THEN 0
            WHEN LOWER(i.name) LIKE LOWER(CONCAT(:search, '%')) THEN 1
            ELSE 2
          END,
          i.level ASC,
          i.name ASC
        """)
    List<Item> searchCatalog(
            @Param("search") String search,
            @Param("itemTypeId") Long itemTypeId,
            @Param("minLevel") Integer minLevel,
            @Param("maxLevel") Integer maxLevel,
            Pageable pageable
    );
}
