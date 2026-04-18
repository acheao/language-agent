package com.acheao.languageagent.controller;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.exception.Result;
import com.acheao.languageagent.service.StatsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public Result<StatsService.OverviewView> getOverview(@AuthenticationPrincipal User user) {
        return Result.success(statsService.getOverview(user));
    }

    @GetMapping("/error-types")
    public Result<List<StatsService.ErrorTypeStatView>> getErrorTypes(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "7d") String range) {
        return Result.success(statsService.getErrorTypes(user, range));
    }
}
