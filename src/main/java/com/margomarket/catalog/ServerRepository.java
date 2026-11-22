package com.margomarket.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServerRepository extends JpaRepository<Server, Long> {
    List<Server> findAllByOrderByNameAsc();
}