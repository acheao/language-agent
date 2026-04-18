package com.acheao.languageagent.v2.repository;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.v2.entity.UserLlmConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserLlmConfigRepository extends JpaRepository<UserLlmConfig, UUID> {
    List<UserLlmConfig> findAllByUserOrderByCreatedAtDesc(User user);

    Optional<UserLlmConfig> findByIdAndUser(UUID id, User user);

    Optional<UserLlmConfig> findFirstByUserAndIsDefaultTrueAndEnabledTrue(User user);

    boolean existsByUser(User user);
}
