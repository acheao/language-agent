package com.acheao.languageagent.service;

import com.acheao.languageagent.client.LlmClient;
import com.acheao.languageagent.entity.Answer;
import com.acheao.languageagent.entity.Grading;
import com.acheao.languageagent.entity.MaterialStats;
import com.acheao.languageagent.entity.Question;
import com.acheao.languageagent.exception.ResourceNotFoundException;
import com.acheao.languageagent.repository.GradingRepository;
import com.acheao.languageagent.repository.MaterialStatsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class GradingService {

    private final LlmClient llmClient;
    private final GradingRepository gradingRepository;
    private final MaterialStatsRepository materialStatsRepository;
    private final ObjectMapper objectMapper;

    public GradingService(LlmClient llmClient, GradingRepository gradingRepository,
            MaterialStatsRepository materialStatsRepository, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.gradingRepository = gradingRepository;
        this.materialStatsRepository = materialStatsRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Grading grade(Question question, Answer answer) {
        LlmClient.GradingResult llmResult = llmClient.gradeAnswer(
                question.getPrompt(),
                question.getReferenceAnswer(),
                question.getRubric(),
                answer.getUserAnswer());

        Grading grading = new Grading();
        grading.setAnswerId(answer.getId());
        grading.setScore(llmResult.getResponse().getScore());
        grading.setCorrect(llmResult.getResponse().isCorrect());
        grading.setCorrectedAnswer(llmResult.getResponse().getCorrectedAnswer());
        grading.setExplanationZh(llmResult.getResponse().getExplanationZh());
        grading.setRawLlmResponse(llmResult.getRawResponse());

        try {
            grading.setErrorTypes(objectMapper.writeValueAsString(llmResult.getResponse().getErrorTypes()));
            grading.setSuggestions(objectMapper.writeValueAsString(llmResult.getResponse().getSuggestions()));
        } catch (JsonProcessingException e) {
            // Ignore serialization errors in MVP
        }

        Grading savedGrading = gradingRepository.save(grading);

        if (question.getMaterialId() != null) {
            updateMaterialStats(question.getMaterialId(), savedGrading.isCorrect());
        }

        return savedGrading;
    }

    private void updateMaterialStats(java.util.UUID materialId, boolean isCorrect) {
        MaterialStats stats = materialStatsRepository.findById(materialId)
                .orElseThrow(() -> new ResourceNotFoundException("Material stats not found"));

        stats.setPracticeCount(stats.getPracticeCount() + 1);
        if (isCorrect) {
            stats.setCorrectCount(stats.getCorrectCount() + 1);
        }

        stats.setLastPracticedAt(LocalDateTime.now());

        // Simplified SM-2 logic
        if (isCorrect) {
            if (stats.getIntervalDays() == 0) {
                stats.setIntervalDays(1);
            } else if (stats.getIntervalDays() == 1) {
                stats.setIntervalDays(6);
            } else {
                stats.setIntervalDays((int) Math.round(stats.getIntervalDays() * stats.getEase()));
            }
            stats.setEase(stats.getEase() + 0.1);
        } else {
            stats.setIntervalDays(1);
            stats.setEase(Math.max(1.3, stats.getEase() - 0.2));
        }

        stats.setNextReviewAt(LocalDateTime.now().plusDays(stats.getIntervalDays()));
        stats.setCooldownUntil(LocalDateTime.now().plusMinutes(30));

        materialStatsRepository.save(stats);
    }
}
