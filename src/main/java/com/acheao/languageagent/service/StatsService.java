package com.acheao.languageagent.service;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.v2.entity.PracticeSubmission;
import com.acheao.languageagent.v2.entity.StudyUnit;
import com.acheao.languageagent.v2.repository.LessonRepository;
import com.acheao.languageagent.v2.repository.PracticeSessionV2Repository;
import com.acheao.languageagent.v2.repository.PracticeSubmissionRepository;
import com.acheao.languageagent.v2.repository.StudyUnitRepository;
import com.acheao.languageagent.v2.service.ErrorTypeCatalog;
import com.acheao.languageagent.v2.service.UserLlmConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.acheao.languageagent.exception.BusinessException;
import com.acheao.languageagent.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class StatsService {

    private final StudyUnitRepository studyUnitRepository;
    private final LessonRepository lessonRepository;
    private final PracticeSessionV2Repository practiceSessionRepository;
    private final PracticeSubmissionRepository practiceSubmissionRepository;
    private final UserLlmConfigService userLlmConfigService;
    private final ObjectMapper objectMapper;

    public StatsService(
            StudyUnitRepository studyUnitRepository,
            LessonRepository lessonRepository,
            PracticeSessionV2Repository practiceSessionRepository,
            PracticeSubmissionRepository practiceSubmissionRepository,
            UserLlmConfigService userLlmConfigService,
            ObjectMapper objectMapper) {
        this.studyUnitRepository = studyUnitRepository;
        this.lessonRepository = lessonRepository;
        this.practiceSessionRepository = practiceSessionRepository;
        this.practiceSubmissionRepository = practiceSubmissionRepository;
        this.userLlmConfigService = userLlmConfigService;
        this.objectMapper = objectMapper;
    }

    public OverviewView getOverview(User user) {
        List<StudyUnit> units = studyUnitRepository.findAllByUserOrderByUpdatedAtDesc(user);
        List<PracticeSubmission> last30Days = practiceSubmissionRepository.findByUserAndCreatedAtAfterOrderByCreatedAtAsc(
                user,
                LocalDateTime.now().minusDays(30));
        List<PracticeSubmission> last7Days = last30Days.stream()
                .filter(item -> item.getCreatedAt().isAfter(LocalDateTime.now().minusDays(7)))
                .toList();

        long masteredUnits = units.stream().filter(unit -> unit.getMasteryScore() >= 85).count();
        long pendingReviewUnits = units.stream()
                .filter(unit -> unit.getNextReviewAt() != null && unit.getNextReviewAt().isBefore(LocalDateTime.now()))
                .count();
        double averageScore = last30Days.stream()
                .mapToInt(PracticeSubmission::getScore)
                .average()
                .orElse(0);
        int totalMinutes = (int) Math.round(last30Days.stream().mapToInt(PracticeSubmission::getDurationSeconds).sum() / 60.0);

        return new OverviewView(
                user.getDailyGoalMinutes() == null ? 30 : user.getDailyGoalMinutes(),
                calculateStreak(last30Days),
                units.size(),
                masteredUnits,
                pendingReviewUnits,
                lessonRepository.findAllByUserOrderByCreatedAtDesc(user).size(),
                practiceSessionRepository.countByUserAndStatus(user, "COMPLETED"),
                Math.round(averageScore * 10.0) / 10.0,
                totalMinutes,
                (int) Math.round(last7Days.stream().mapToInt(PracticeSubmission::getDurationSeconds).sum() / 60.0),
                userLlmConfigService.hasAnyConfig(user),
                buildLast7Days(last30Days));
    }

    public List<ErrorTypeStatView> getErrorTypes(User user, String range) {
        int days = resolveRangeDays(range);
        List<PracticeSubmission> submissions = practiceSubmissionRepository.findByUserAndCreatedAtAfterOrderByCreatedAtAsc(
                user,
                LocalDateTime.now().minusDays(days));
        Map<String, ErrorTypeAggregate> counts = new LinkedHashMap<>();
        for (PracticeSubmission submission : submissions) {
            for (String errorType : parseErrorTypes(submission.getErrorTypesJson())) {
                counts.computeIfAbsent(errorType, key -> new ErrorTypeAggregate())
                        .record(submission.getCreatedAt());
            }
        }

        return counts.entrySet().stream()
                .sorted((left, right) -> {
                    int countCompare = Integer.compare(right.getValue().count(), left.getValue().count());
                    if (countCompare != 0) {
                        return countCompare;
                    }
                    LocalDateTime leftTime = left.getValue().lastSeenAt();
                    LocalDateTime rightTime = right.getValue().lastSeenAt();
                    if (leftTime == null && rightTime == null) {
                        return 0;
                    }
                    if (leftTime == null) {
                        return 1;
                    }
                    if (rightTime == null) {
                        return -1;
                    }
                    return rightTime.compareTo(leftTime);
                })
                .map(entry -> new ErrorTypeStatView(entry.getKey(), entry.getValue().count(), entry.getValue().lastSeenAt()))
                .toList();
    }

    private int calculateStreak(List<PracticeSubmission> submissions) {
        Set<LocalDate> activeDays = new TreeSet<>();
        submissions.forEach(item -> activeDays.add(item.getCreatedAt().toLocalDate()));
        int streak = 0;
        LocalDate cursor = LocalDate.now();
        while (activeDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private List<DayProgressView> buildLast7Days(List<PracticeSubmission> submissions) {
        List<DayProgressView> result = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            List<PracticeSubmission> dayItems = submissions.stream()
                    .filter(item -> item.getCreatedAt().toLocalDate().equals(date))
                    .sorted(Comparator.comparing(PracticeSubmission::getCreatedAt))
                    .toList();
            int minutes = (int) Math.round(dayItems.stream().mapToInt(PracticeSubmission::getDurationSeconds).sum() / 60.0);
            double avgScore = dayItems.stream().mapToInt(PracticeSubmission::getScore).average().orElse(0);
            result.add(new DayProgressView(date, minutes, Math.round(avgScore * 10.0) / 10.0, dayItems.size()));
        }
        return result;
    }

    private List<String> parseErrorTypes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return ErrorTypeCatalog.normalizeAll(parsed);
        } catch (Exception e) {
            return List.of();
        }
    }

    private int resolveRangeDays(String range) {
        return switch (range == null ? "" : range.trim().toLowerCase()) {
            case "7d", "" -> 7;
            case "30d" -> 30;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported range: " + range);
        };
    }

    public record OverviewView(
            int dailyGoalMinutes,
            int streakDays,
            int studyUnits,
            long masteredUnits,
            long pendingReviewUnits,
            int activeLessons,
            long sessionsCompleted,
            double recentAverageScore,
            int practiceMinutesLast30Days,
            int practiceMinutesLast7Days,
            boolean hasLlmConfig,
            List<DayProgressView> last7Days) {
    }

    public record DayProgressView(LocalDate date, int minutes, double avgScore, int answers) {
    }

    public record ErrorTypeStatView(String errorType, int count, LocalDateTime lastSeenAt) {
    }

    private static final class ErrorTypeAggregate {
        private int count;
        private LocalDateTime lastSeenAt;

        void record(LocalDateTime seenAt) {
            count++;
            if (lastSeenAt == null || (seenAt != null && seenAt.isAfter(lastSeenAt))) {
                lastSeenAt = seenAt;
            }
        }

        int count() {
            return count;
        }

        LocalDateTime lastSeenAt() {
            return lastSeenAt;
        }
    }
}
