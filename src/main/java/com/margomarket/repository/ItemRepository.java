package com.margomarket.repository;

import com.margomarket.model.Item;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByExternalId(Long externalId);

    @Modifying
    @Query("UPDATE Item i SET i.marketEnabled = false")
    void disableMarketCatalog();

    @EntityGraph(attributePaths = {"itemType", "rarity"})
    @Query("""
        SELECT i
        FROM Item i
        WHERE i.marketEnabled = true
          AND (:search = '' OR LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:itemTypeId IS NULL OR i.itemType.id = :itemTypeId)
          AND (:level IS NULL OR i.level = :level)
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
            @Param("level") Integer level,
            Pageable pageable
    );
}
