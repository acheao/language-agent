package com.acheao.languageagent.service;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.v2.entity.PracticeSubmission;
import com.acheao.languageagent.v2.repository.LessonRepository;
import com.acheao.languageagent.v2.repository.PracticeSessionV2Repository;
import com.acheao.languageagent.v2.repository.PracticeSubmissionRepository;
import com.acheao.languageagent.v2.repository.StudyUnitRepository;
import com.acheao.languageagent.v2.service.UserLlmConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatsServiceTest {

    private final StudyUnitRepository studyUnitRepository = mock(StudyUnitRepository.class);
    private final LessonRepository lessonRepository = mock(LessonRepository.class);
    private final PracticeSessionV2Repository practiceSessionRepository = mock(PracticeSessionV2Repository.class);
    private final PracticeSubmissionRepository practiceSubmissionRepository = mock(PracticeSubmissionRepository.class);
    private final UserLlmConfigService userLlmConfigService = mock(UserLlmConfigService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final StatsService statsService = new StatsService(
            studyUnitRepository,
            lessonRepository,
            practiceSessionRepository,
            practiceSubmissionRepository,
            userLlmConfigService,
            objectMapper);

    @Test
    void getErrorTypes_normalizesAndRespectsRange() throws Exception {
        User user = new User();
        user.setEmail("test@example.com");

        PracticeSubmission recent = new PracticeSubmission();
        recent.setErrorTypesJson(objectMapper.writeValueAsString(List.of("wording", "punctuation")));
        recent.setScore(70);
        recent.setDurationSeconds(420);
        setCreatedAt(recent, LocalDateTime.now().minusDays(2));

        PracticeSubmission older = new PracticeSubmission();
        older.setErrorTypesJson(objectMapper.writeValueAsString(List.of("listening", "accuracy")));
        older.setScore(55);
        older.setDurationSeconds(360);
        setCreatedAt(older, LocalDateTime.now().minusDays(20));

        when(practiceSubmissionRepository.findByUserAndCreatedAtAfterOrderByCreatedAtAsc(any(), any()))
                .thenAnswer(invocation -> {
                    LocalDateTime threshold = invocation.getArgument(1);
                    return List.of(recent, older).stream()
                            .filter(item -> item.getCreatedAt().isAfter(threshold))
                            .toList();
                });

        List<StatsService.ErrorTypeStatView> last7Days = statsService.getErrorTypes(user, "7d");
        List<StatsService.ErrorTypeStatView> last30Days = statsService.getErrorTypes(user, "30d");

        assertThat(last7Days).extracting(StatsService.ErrorTypeStatView::errorType)
                .containsExactlyInAnyOrder("word_choice", "punctuation");
        assertThat(last30Days).extracting(StatsService.ErrorTypeStatView::errorType)
                .containsExactlyInAnyOrder("word_choice", "punctuation", "listening_accuracy", "completeness");
    }

    private void setCreatedAt(PracticeSubmission submission, LocalDateTime createdAt) throws Exception {
        var field = PracticeSubmission.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(submission, createdAt);
    }
}
