package com.acheao.languageagent.v2.repository;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.v2.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    List<Lesson> findAllByUserOrderByCreatedAtDesc(User user);

    Optional<Lesson> findByIdAndUser(UUID id, User user);
}
