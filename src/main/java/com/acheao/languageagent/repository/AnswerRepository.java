package com.acheao.languageagent.repository;

import com.acheao.languageagent.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, UUID> {
    Answer findByQuestionId(UUID questionId);
}
