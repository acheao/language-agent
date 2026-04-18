package com.acheao.languageagent.v2.repository;

import com.acheao.languageagent.v2.entity.PracticeSession;
import com.acheao.languageagent.v2.entity.PracticeTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PracticeTaskRepository extends JpaRepository<PracticeTask, UUID> {
    List<PracticeTask> findAllByPracticeSessionOrderByOrderIndexAsc(PracticeSession practiceSession);

    Optional<PracticeTask> findFirstByPracticeSessionAndOrderIndex(PracticeSession practiceSession, Integer orderIndex);
}
