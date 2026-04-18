package com.acheao.languageagent.v2.repository;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.v2.entity.PracticeSubmission;
import com.acheao.languageagent.v2.entity.PracticeTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PracticeSubmissionRepository extends JpaRepository<PracticeSubmission, UUID> {
    List<PracticeSubmission> findTop200ByUserOrderByCreatedAtDesc(User user);

    List<PracticeSubmission> findByPracticeTask(PracticeTask practiceTask);

    List<PracticeSubmission> findByUserAndCreatedAtAfterOrderByCreatedAtAsc(User user, LocalDateTime createdAt);
}
