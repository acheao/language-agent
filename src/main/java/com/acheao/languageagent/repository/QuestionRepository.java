package com.acheao.languageagent.repository;

import com.acheao.languageagent.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {
    List<Question> findBySessionId(UUID sessionId);
    boolean existsByMaterialId(UUID materialId);

    @Query(value = """
            SELECT
                q.id AS questionId,
                q.material_id AS materialId,
                q.type AS questionType,
                a.user_answer AS userAnswer,
                g.corrected_answer AS correctedAnswer,
                CAST(g.error_types AS text) AS errorTypes,
                g.explanation_zh AS explanationZh,
                g.created_at AS wrongAt
            FROM gradings g
            JOIN answers a ON g.answer_id = a.id
            JOIN questions q ON a.question_id = q.id
            WHERE g.is_correct = false
            ORDER BY g.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<WrongAttemptProjection> findLatestWrongAttempts(@Param("limit") int limit);

    interface WrongAttemptProjection {
        UUID getQuestionId();
        UUID getMaterialId();
        String getQuestionType();
        String getUserAnswer();
        String getCorrectedAnswer();
        String getErrorTypes();
        String getExplanationZh();
        LocalDateTime getWrongAt();
    }
}
