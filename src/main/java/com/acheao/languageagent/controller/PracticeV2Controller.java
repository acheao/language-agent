package com.acheao.languageagent.controller;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.exception.Result;
import com.acheao.languageagent.v2.service.PracticeV2Service;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/practice")
public class PracticeV2Controller {

    private final PracticeV2Service practiceV2Service;

    public PracticeV2Controller(PracticeV2Service practiceV2Service) {
        this.practiceV2Service = practiceV2Service;
    }

    @PostMapping("/sessions")
    public Result<PracticeV2Service.SessionView> startSession(
            @AuthenticationPrincipal User user,
            @RequestBody PracticeV2Service.StartSessionRequest request) {
        return Result.success(practiceV2Service.startSession(user, request));
    }

    @GetMapping("/sessions/{id}")
    public Result<PracticeV2Service.SessionView> getSession(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        return Result.success(practiceV2Service.getSession(user, id));
    }

    @PostMapping("/answers")
    public Result<PracticeV2Service.AnswerResult> submitAnswer(
            @AuthenticationPrincipal User user,
            @RequestBody PracticeV2Service.SubmitAnswerRequest request) {
        return Result.success(practiceV2Service.submitAnswer(user, request));
    }
}
