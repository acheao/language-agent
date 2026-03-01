package com.acheao.languageagent.controller;

import com.acheao.languageagent.dto.AnswerSubmitRequest;
import com.acheao.languageagent.dto.SessionCreateRequest;
import com.acheao.languageagent.entity.Grading;
import com.acheao.languageagent.service.AnswerService;
import com.acheao.languageagent.service.SessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
@Tag(name = "Practice", description = "Endpoints for practice sessions and grading")
public class PracticeController {

    private static final Logger log = LoggerFactory.getLogger(PracticeController.class);

    private final SessionService sessionService;
    private final AnswerService answerService;
    private final ObjectMapper objectMapper;

    public PracticeController(SessionService sessionService, AnswerService answerService, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.answerService = answerService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/sessions")
    @Operation(summary = "Create a new session",
            description = "Generates questions by mode: new, wrong, review, smart")
    public ResponseEntity<SessionService.SessionResult> createSession(@RequestBody SessionCreateRequest request) {
        return ResponseEntity
                .ok(sessionService.createSessionAndQuestions(request.getBatchSize(), request.getGeneratorMode()));
    }

    @PostMapping("/sessions/{sessionId}/next")
    @Operation(summary = "Get next batch",
            description = "Continues the session with a new batch by mode: new, wrong, review, smart")
    public ResponseEntity<SessionService.SessionResult> getNextBatch(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "10") int batchSize,
            @RequestParam(defaultValue = "new") String generatorMode) {
        // MVP: Resuing create logic but assigning a new session. Could link to old
        // session in V2.
        return ResponseEntity.ok(sessionService.createSessionAndQuestions(batchSize, generatorMode));
    }

    @PostMapping("/answers")
    @Operation(summary = "Submit an answer", description = "Grades the provided answer using LLM")
    public ResponseEntity<Grading> submitAnswer(@RequestBody AnswerSubmitRequest request) {
        return ResponseEntity.ok(answerService.submitAnswer(request));
    }

    @PostMapping(value = "/answers/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Submit an answer with streaming grading",
            description = "Grades one question and streams parsed explanation chunks to frontend")
    public SseEmitter submitAnswerStream(@RequestBody AnswerSubmitRequest request) {
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(5));

        CompletableFuture.runAsync(() -> {
            try {
                sendEvent(emitter, "status", Map.of(
                        "stage", "accepted",
                        "timestamp", LocalDateTime.now().toString()));
                sendEvent(emitter, "status", Map.of(
                        "stage", "grading",
                        "message", "Calling LLM for grading"));

                Grading grading = answerService.submitAnswer(request);
                List<String> errorTypes = parseJsonArray(grading.getErrorTypes());
                List<String> suggestions = parseJsonArray(grading.getSuggestions());

                sendEvent(emitter, "result_meta", Map.of(
                        "gradingId", grading.getId(),
                        "answerId", grading.getAnswerId(),
                        "score", grading.getScore(),
                        "isCorrect", grading.isCorrect(),
                        "correctedAnswer", safe(grading.getCorrectedAnswer()),
                        "errorTypes", errorTypes));

                String explanation = safe(grading.getExplanationZh());
                if (!explanation.isBlank()) {
                    for (String chunk : splitByLength(explanation, 24)) {
                        sendEvent(emitter, "analysis_chunk", Map.of("content", chunk));
                        sleepSilently(45);
                    }
                }

                for (String suggestion : suggestions) {
                    sendEvent(emitter, "suggestion", Map.of("content", suggestion));
                    sleepSilently(45);
                }

                sendEvent(emitter, "done", Map.of(
                        "gradingId", grading.getId(),
                        "createdAt", grading.getCreatedAt() == null ? null : grading.getCreatedAt().toString()));
                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming grading failed: {}", e.getMessage(), e);
                try {
                    sendEvent(emitter, "error", Map.of("message", e.getMessage()));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }

    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return Collections.singletonList(json);
        }
    }

    private List<String> splitByLength(String text, int chunkSize) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        int size = Math.max(1, chunkSize);
        List<String> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(i + size, text.length())));
        }
        return chunks;
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
