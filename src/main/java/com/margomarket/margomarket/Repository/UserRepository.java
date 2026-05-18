package com.margomarket.margomarket.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.margomarket.margomarket.model.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT COUNT(u) FROM User u")
    long countAllUsers();

    @Query("""
        SELECT COUNT(u) FROM User u
        INNER JOIN u.role r
        WHERE r.name = 'admin'
        """)
    long countAdmins();
}