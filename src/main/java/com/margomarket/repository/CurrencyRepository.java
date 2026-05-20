package com.margomarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.margomarket.model.Currency;

import java.util.List;

public interface CurrencyRepository extends JpaRepository<Currency, Long> {
    List<Currency> findAllByOrderByIdAsc();
}