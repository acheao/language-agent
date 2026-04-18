package com.acheao.languageagent.repository;

import com.acheao.languageagent.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    default Optional<User> findByUsername(String username) {
        return findByEmail(username);
    }

    default boolean existsByUsername(String username) {
        return existsByEmail(username);
    }
}
