package com.acheao.languageagent.repository;

import com.acheao.languageagent.entity.Grading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GradingRepository extends JpaRepository<Grading, UUID> {
    Grading findByAnswerId(UUID answerId);

    @Query(value = """
            SELECT
                et.error_type AS errorType,
                COUNT(*) AS totalCount
            FROM gradings g
            JOIN LATERAL jsonb_array_elements_text(COALESCE(g.error_types, CAST('[]' AS jsonb))) et(error_type) ON true
            WHERE g.is_correct = false
            GROUP BY et.error_type
            ORDER BY COUNT(*) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ErrorTypeRankProjection> findTopWrongErrorTypes(@Param("limit") int limit);

    interface ErrorTypeRankProjection {
        String getErrorType();
        Long getTotalCount();
    }
}
