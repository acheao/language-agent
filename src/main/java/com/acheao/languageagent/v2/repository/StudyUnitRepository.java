package com.acheao.languageagent.v2.repository;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.v2.entity.Lesson;
import com.acheao.languageagent.v2.entity.StudyUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyUnitRepository extends JpaRepository<StudyUnit, UUID> {
    List<StudyUnit> findAllByUserOrderByUpdatedAtDesc(User user);

    List<StudyUnit> findAllByLessonOrderByOrderIndexAsc(Lesson lesson);

    Optional<StudyUnit> findByIdAndUser(UUID id, User user);

    List<StudyUnit> findTop50ByUserAndIgnoredFalseAndInPracticePoolTrueAndNextReviewAtBeforeOrderByNextReviewAtAsc(User user,
            LocalDateTime nextReviewAt);

    List<StudyUnit> findTop50ByUserAndIgnoredFalseAndInPracticePoolTrueAndMasteryScoreLessThanOrderByMasteryScoreAsc(User user,
            Integer masteryScore);

    List<StudyUnit> findTop50ByUserAndIgnoredFalseAndInPracticePoolTrueAndAttemptsLessThanEqualOrderByCreatedAtDesc(User user,
            Integer attempts);
}
