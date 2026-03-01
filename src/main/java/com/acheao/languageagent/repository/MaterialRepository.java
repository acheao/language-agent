package com.acheao.languageagent.repository;

import com.acheao.languageagent.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {
    boolean existsByContent(String content);

    @Query("""
            SELECT m FROM Material m
            WHERE m.enabled = true
            AND NOT EXISTS (
                SELECT 1 FROM Question q WHERE q.materialId = m.id
            )
            ORDER BY m.createdAt DESC
            """)
    List<Material> findEnabledMaterialsWithoutQuestions(Pageable pageable);
}
