package com.acheao.languageagent.controller;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.exception.Result;
import com.acheao.languageagent.v2.service.UserLlmConfigService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/settings/llm")
public class LlmSettingsController {

    private final UserLlmConfigService userLlmConfigService;

    public LlmSettingsController(UserLlmConfigService userLlmConfigService) {
        this.userLlmConfigService = userLlmConfigService;
    }

    @GetMapping
    public Result<List<UserLlmConfigService.ConfigView>> list(@AuthenticationPrincipal User user) {
        return Result.success(userLlmConfigService.list(user));
    }

    @GetMapping("/providers")
    public Result<List<UserLlmConfigService.ProviderCatalogItem>> providers() {
        return Result.success(userLlmConfigService.providers());
    }

    @PostMapping
    public Result<UserLlmConfigService.ConfigView> create(
            @AuthenticationPrincipal User user,
            @RequestBody UserLlmConfigService.UpsertConfigRequest request) {
        return Result.success(userLlmConfigService.create(user, request));
    }

    @PatchMapping("/{id}")
    public Result<UserLlmConfigService.ConfigView> update(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @RequestBody UserLlmConfigService.UpsertConfigRequest request) {
        return Result.success(userLlmConfigService.update(user, id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        userLlmConfigService.delete(user, id);
        return Result.success(true);
    }

    @PostMapping("/{id}/test")
    public Result<UserLlmConfigService.TestResult> test(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return Result.success(userLlmConfigService.test(user, id));
    }
}
