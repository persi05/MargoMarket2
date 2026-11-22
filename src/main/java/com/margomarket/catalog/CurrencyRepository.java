package com.margomarket.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    java.util.Optional<Currency> findByNameIgnoreCase(String name);
    List<Currency> findAllByOrderByIdAsc();
}