package com.acheao.languageagent.controller;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.exception.Result;
import com.acheao.languageagent.v2.service.LessonWorkflowService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/study-units")
public class StudyUnitController {

    private final LessonWorkflowService lessonWorkflowService;

    public StudyUnitController(LessonWorkflowService lessonWorkflowService) {
        this.lessonWorkflowService = lessonWorkflowService;
    }

    @PatchMapping("/{id}")
    public Result<LessonWorkflowService.StudyUnitView> update(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @RequestBody LessonWorkflowService.UpdateStudyUnitRequest request) {
        return Result.success(lessonWorkflowService.updateStudyUnit(user, id, request));
    }
}
