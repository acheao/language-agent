package com.acheao.languageagent.repository;

import com.acheao.languageagent.entity.MaterialStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialStatsRepository extends JpaRepository<MaterialStats, UUID> {
    
    @Query("""
        SELECT ms FROM MaterialStats ms 
        JOIN Material m ON ms.materialId = m.id 
        WHERE m.enabled = true 
        AND (ms.cooldownUntil IS NULL OR ms.cooldownUntil < :now)
        ORDER BY 
            CASE WHEN ms.nextReviewAt IS NULL OR ms.nextReviewAt <= :now THEN 1 ELSE 0 END DESC,
            ms.nextReviewAt ASC
        """)
    List<MaterialStats> findDueMaterials(LocalDateTime now);
}
