package com.margomarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.margomarket.model.Currency;

import java.util.List;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    java.util.Optional<Currency> findByNameIgnoreCase(String name);
    List<Currency> findAllByOrderByIdAsc();
}