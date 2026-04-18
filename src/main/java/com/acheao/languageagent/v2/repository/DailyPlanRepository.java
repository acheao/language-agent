package com.acheao.languageagent.v2.repository;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.v2.entity.DailyPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DailyPlanRepository extends JpaRepository<DailyPlan, UUID> {
    Optional<DailyPlan> findByUserAndPlanDate(User user, LocalDate planDate);
}
