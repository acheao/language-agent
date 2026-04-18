package com.acheao.languageagent.controller;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.exception.Result;
import com.acheao.languageagent.v2.service.LessonWorkflowService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lessons")
public class LessonControllerV2 {

    private final LessonWorkflowService lessonWorkflowService;

    public LessonControllerV2(LessonWorkflowService lessonWorkflowService) {
        this.lessonWorkflowService = lessonWorkflowService;
    }

    @GetMapping
    public Result<List<LessonWorkflowService.LessonSummaryView>> list(@AuthenticationPrincipal User user) {
        return Result.success(lessonWorkflowService.listLessons(user));
    }

    @GetMapping("/{id}")
    public Result<LessonWorkflowService.LessonDetailView> detail(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        return Result.success(lessonWorkflowService.getLessonDetail(user, id));
    }

    @GetMapping("/{id}/media")
    public ResponseEntity<Resource> media(@AuthenticationPrincipal User user, @PathVariable UUID id) throws Exception {
        Path mediaPath = lessonWorkflowService.resolveMediaPath(user, id);
        String contentType = Files.probeContentType(mediaPath);
        return ResponseEntity.ok()
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(Files.newInputStream(mediaPath)));
    }
}
