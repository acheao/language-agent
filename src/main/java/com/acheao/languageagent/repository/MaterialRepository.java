package com.acheao.languageagent.repository;

import com.acheao.languageagent.entity.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {
    boolean existsByContent(String content);
}
