package com.margomarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.margomarket.model.Server;

import java.util.List;

public interface ServerRepository extends JpaRepository<Server, Long> {
    List<Server> findAllByOrderByNameAsc();
}