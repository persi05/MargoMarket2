package com.margomarket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import com.margomarket.model.User;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Modifying
    @Query("DELETE FROM User u WHERE u.emailVerified = false AND u.verificationExpiresAt <= :now")
    int deleteExpiredUnverified(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM User u WHERE u.emailVerified = false AND u.verificationExpiresAt <= :now "
            + "AND (u.email = :email OR u.username = :username)")
    int deleteExpiredUnverifiedMatching(@Param("now") LocalDateTime now,
                                        @Param("email") String email, @Param("username") String username);

    @Query("SELECT COUNT(u) FROM User u")
    long countAllUsers();

    @Query("""
        SELECT COUNT(u) FROM User u
        INNER JOIN u.role r
        WHERE r.name = 'admin'
        """)
    long countAdmins();
}
