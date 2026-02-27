package com.acheao.languageagent.repository;

import com.acheao.languageagent.entity.Grading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GradingRepository extends JpaRepository<Grading, UUID> {
    Grading findByAnswerId(UUID answerId);
}
