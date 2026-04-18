package com.acheao.languageagent.v2.service;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.v2.entity.DailyPlan;
import com.acheao.languageagent.v2.entity.StudyUnit;
import com.acheao.languageagent.v2.repository.DailyPlanRepository;
import com.acheao.languageagent.v2.repository.StudyUnitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DailyPlanService {

    private final DailyPlanRepository dailyPlanRepository;
    private final StudyUnitRepository studyUnitRepository;
    private final ObjectMapper objectMapper;

    public DailyPlanService(
            DailyPlanRepository dailyPlanRepository,
            StudyUnitRepository studyUnitRepository,
            ObjectMapper objectMapper) {
        this.dailyPlanRepository = dailyPlanRepository;
        this.studyUnitRepository = studyUnitRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TodayPlanView getTodayPlan(User user) {
        DailyPlan plan = getOrCreateEntity(user);
        List<SelectedStudyUnit> units = selectDailyUnits(user, targetTaskCount(user));
        return toView(plan, units);
    }

    @Transactional
    public DailyPlan getOrCreateEntity(User user) {
        return dailyPlanRepository.findByUserAndPlanDate(user, LocalDate.now())
                .orElseGet(() -> createPlan(user));
    }

    public List<SelectedStudyUnit> selectDailyUnits(User user, int limit) {
        return buildSelection(user, limit, false);
    }

    public List<SelectedStudyUnit> selectExtraUnits(User user, int limit) {
        return buildSelection(user, Math.max(limit, 6), true);
    }

    private DailyPlan createPlan(User user) {
        List<SelectedStudyUnit> units = buildSelection(user, targetTaskCount(user), false);
        Map<String, Long> bucketCounts = units.stream()
                .collect(java.util.stream.Collectors.groupingBy(SelectedStudyUnit::bucket, LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));

        DailyPlan plan = new DailyPlan();
        plan.setUser(user);
        plan.setPlanDate(LocalDate.now());
        plan.setEstimatedMinutes(defaultMinutes(user));
        plan.setMajorFocus(majorFocus(bucketCounts));
        plan.setFocusSummary(buildSummary(bucketCounts, defaultMinutes(user)));
        plan.setItemCountsJson(writeJson(bucketCounts));
        plan.setStatus("READY");
        return dailyPlanRepository.save(plan);
    }

    private List<SelectedStudyUnit> buildSelection(User user, int limit, boolean extraMode) {
        int safeLimit = Math.max(4, limit);
        List<SelectedStudyUnit> selected = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        addBucket(selected,
                rankCandidates(
                        studyUnitRepository.findTop50ByUserAndIgnoredFalseAndInPracticePoolTrueAndNextReviewAtBeforeOrderByNextReviewAtAsc(user, now),
                        "due",
                        now),
                extraMode ? safeLimit / 3 : Math.max(2, safeLimit / 2),
                "due");
        addBucket(selected,
                rankCandidates(
                        studyUnitRepository.findTop50ByUserAndIgnoredFalseAndInPracticePoolTrueAndMasteryScoreLessThanOrderByMasteryScoreAsc(user, 65),
                        "weak",
                        now),
                extraMode ? safeLimit / 2 : Math.max(2, safeLimit / 3),
                "weak");
        addBucket(selected,
                rankCandidates(
                        studyUnitRepository.findTop50ByUserAndIgnoredFalseAndInPracticePoolTrueAndAttemptsLessThanEqualOrderByCreatedAtDesc(user, 0),
                        "new",
                        now),
                Math.max(1, safeLimit / 4),
                "new");

        if (selected.size() < safeLimit) {
            for (StudyUnit unit : rankCandidates(studyUnitRepository.findAllByUserOrderByUpdatedAtDesc(user),
                    extraMode ? "challenge" : "mix",
                    now)) {
                if (unit.isIgnored() || !unit.isInPracticePool() || contains(selected, unit)) {
                    continue;
                }
                selected.add(new SelectedStudyUnit(unit, extraMode ? "challenge" : "mix"));
                if (selected.size() >= safeLimit) {
                    break;
                }
            }
        }
        return selected;
    }

    private void addBucket(List<SelectedStudyUnit> selected, List<StudyUnit> candidates, int limit, String bucket) {
        if (limit <= 0) {
            return;
        }
        for (StudyUnit unit : candidates) {
            if (contains(selected, unit)) {
                continue;
            }
            selected.add(new SelectedStudyUnit(unit, bucket));
            if (selected.size() >= 50 || selected.stream().filter(item -> item.bucket().equals(bucket)).count() >= limit) {
                break;
            }
        }
    }

    private boolean contains(List<SelectedStudyUnit> selected, StudyUnit unit) {
        return selected.stream().anyMatch(item -> item.studyUnit().getId().equals(unit.getId()));
    }

    private List<StudyUnit> rankCandidates(List<StudyUnit> units, String bucket, LocalDateTime now) {
        return units.stream()
                .sorted(Comparator
                        .comparingDouble((StudyUnit unit) -> priorityScore(unit, bucket, now)).reversed()
                        .thenComparing(StudyUnit::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private double priorityScore(StudyUnit unit, String bucket, LocalDateTime now) {
        double score = 0.0;

        if ("due".equals(bucket)) {
            if (unit.getNextReviewAt() == null || !unit.getNextReviewAt().isAfter(now)) {
                double overdueHours = unit.getNextReviewAt() == null
                        ? 12.0
                        : Math.max(0.0, Duration.between(unit.getNextReviewAt(), now).toHours());
                score += 2.0 + Math.min(6.0, overdueHours / 6.0);
            }
        }

        if ("weak".equals(bucket)) {
            score += Math.max(0.0, (100 - unit.getMasteryScore()) / 12.0);
        }

        if ("new".equals(bucket)) {
            score += Math.max(0, 3 - unit.getAttempts());
        }

        score += Math.min(3.0, unit.getAverageDurationSeconds() / 25.0);
        score += Math.min(2.5, unit.getSkipCount() * 0.6);
        score += Math.min(2.5, unit.getUncertainCount() * 0.7);
        score += unit.getLastErrorType() == null || unit.getLastErrorType().isBlank() ? 0.0 : 0.8;
        score += Math.max(0.0, (80 - unit.getMasteryScore()) / 20.0);
        score += Math.max(0.0, (unit.getDifficulty() - 2) * 0.3);

        return score;
    }

    private int targetTaskCount(User user) {
        return Math.max(6, defaultMinutes(user) / 3);
    }

    private int defaultMinutes(User user) {
        return user.getDailyGoalMinutes() == null ? 30 : Math.max(15, user.getDailyGoalMinutes());
    }

    private String majorFocus(Map<String, Long> bucketCounts) {
        return bucketCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("mix");
    }

    private String buildSummary(Map<String, Long> bucketCounts, int minutes) {
        long due = bucketCounts.getOrDefault("due", 0L);
        long weak = bucketCounts.getOrDefault("weak", 0L);
        long fresh = bucketCounts.getOrDefault("new", 0L);
        return "A " + minutes + "-minute daily pack with " + due + " review items, "
                + weak + " weak-pattern drills, and " + fresh + " fresh units.";
    }

    private TodayPlanView toView(DailyPlan plan, List<SelectedStudyUnit> units) {
        Map<String, Long> bucketCounts;
        try {
            bucketCounts = objectMapper.readValue(plan.getItemCountsJson(), Map.class);
        } catch (Exception e) {
            bucketCounts = Map.of();
        }
        return new TodayPlanView(
                plan.getId(),
                plan.getPlanDate(),
                plan.getEstimatedMinutes(),
                plan.getMajorFocus(),
                plan.getFocusSummary(),
                plan.getStatus(),
                units.size(),
                bucketCounts);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    public record SelectedStudyUnit(StudyUnit studyUnit, String bucket) {
    }

    public record TodayPlanView(
            java.util.UUID id,
            LocalDate planDate,
            int estimatedMinutes,
            String majorFocus,
            String focusSummary,
            String status,
            int selectedUnitCount,
            Map<String, Long> bucketCounts) {
    }
}
