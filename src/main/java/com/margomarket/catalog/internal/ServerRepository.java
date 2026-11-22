package com.margomarket.catalog.internal;

import com.margomarket.catalog.Server;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServerRepository extends JpaRepository<Server, Long> {
    List<Server> findAllByOrderByNameAsc();
}