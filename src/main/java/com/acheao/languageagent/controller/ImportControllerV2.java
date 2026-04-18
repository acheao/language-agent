package com.acheao.languageagent.controller;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.exception.Result;
import com.acheao.languageagent.v2.service.LessonWorkflowService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/import")
public class ImportControllerV2 {

    private final LessonWorkflowService lessonWorkflowService;
    private final TaskExecutor youtubeImportTaskExecutor;

    public ImportControllerV2(
            LessonWorkflowService lessonWorkflowService,
            @Qualifier("youtubeImportTaskExecutor") TaskExecutor youtubeImportTaskExecutor) {
        this.lessonWorkflowService = lessonWorkflowService;
        this.youtubeImportTaskExecutor = youtubeImportTaskExecutor;
    }

    @PostMapping("/text")
    public Result<LessonWorkflowService.LessonSummaryView> importText(
            @AuthenticationPrincipal User user,
            @RequestBody LessonWorkflowService.TextImportRequest request) {
        return Result.success(lessonWorkflowService.importText(user, request));
    }

    @PostMapping("/article")
    public Result<LessonWorkflowService.LessonSummaryView> importArticle(
            @AuthenticationPrincipal User user,
            @RequestBody LessonWorkflowService.ArticleImportRequest request) {
        return Result.success(lessonWorkflowService.importArticle(user, request));
    }

    @PostMapping("/youtube")
    public Result<LessonWorkflowService.LessonSummaryView> importYoutube(
            @AuthenticationPrincipal User user,
            @RequestBody LessonWorkflowService.YoutubeImportRequest request) {
        LessonWorkflowService.LessonSummaryView accepted = lessonWorkflowService.startYoutubeImport(user, request);
        youtubeImportTaskExecutor.execute(() -> lessonWorkflowService.completeYoutubeImport(accepted.id()));
        return Result.success(accepted);
    }
}
