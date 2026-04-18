package com.acheao.languageagent.v2.repository;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.v2.entity.PracticeSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeSessionV2Repository extends JpaRepository<PracticeSession, UUID> {
    Optional<PracticeSession> findByIdAndUser(UUID id, User user);

    List<PracticeSession> findTop20ByUserOrderByCreatedAtDesc(User user);

    long countByUserAndStatus(User user, String status);
}
