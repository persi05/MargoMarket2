package com.margomarket.margomarket.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.margomarket.margomarket.model.Server;

import java.util.List;

public interface ServerRepository extends JpaRepository<Server, Long> {
    List<Server> findAllByOrderByNameAsc();
}