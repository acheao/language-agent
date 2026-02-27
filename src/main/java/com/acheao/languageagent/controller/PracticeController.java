package com.acheao.languageagent.controller;

import com.acheao.languageagent.dto.AnswerSubmitRequest;
import com.acheao.languageagent.dto.SessionCreateRequest;
import com.acheao.languageagent.entity.Grading;
import com.acheao.languageagent.service.AnswerService;
import com.acheao.languageagent.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Practice", description = "Endpoints for practice sessions and grading")
public class PracticeController {

    private final SessionService sessionService;
    private final AnswerService answerService;

    public PracticeController(SessionService sessionService, AnswerService answerService) {
        this.sessionService = sessionService;
        this.answerService = answerService;
    }

    @PostMapping("/sessions")
    @Operation(summary = "Create a new session", description = "Generates questions from scheduled materials")
    public ResponseEntity<SessionService.SessionResult> createSession(@RequestBody SessionCreateRequest request) {
        return ResponseEntity
                .ok(sessionService.createSessionAndQuestions(request.getBatchSize(), request.getGeneratorMode()));
    }

    @PostMapping("/sessions/{sessionId}/next")
    @Operation(summary = "Get next batch", description = "Continues the session with a new batch of questions")
    public ResponseEntity<SessionService.SessionResult> getNextBatch(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "10") int batchSize,
            @RequestParam(defaultValue = "hybrid") String generatorMode) {
        // MVP: Resuing create logic but assigning a new session. Could link to old
        // session in V2.
        return ResponseEntity.ok(sessionService.createSessionAndQuestions(batchSize, generatorMode));
    }

    @PostMapping("/answers")
    @Operation(summary = "Submit an answer", description = "Grades the provided answer using LLM")
    public ResponseEntity<Grading> submitAnswer(@RequestBody AnswerSubmitRequest request) {
        return ResponseEntity.ok(answerService.submitAnswer(request));
    }
}
