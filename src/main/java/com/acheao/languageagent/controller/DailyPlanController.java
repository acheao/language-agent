package com.acheao.languageagent.controller;

import com.acheao.languageagent.domain.entity.User;
import com.acheao.languageagent.exception.Result;
import com.acheao.languageagent.v2.service.DailyPlanService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/daily-plan")
public class DailyPlanController {

    private final DailyPlanService dailyPlanService;

    public DailyPlanController(DailyPlanService dailyPlanService) {
        this.dailyPlanService = dailyPlanService;
    }

    @GetMapping("/today")
    public Result<DailyPlanService.TodayPlanView> today(@AuthenticationPrincipal User user) {
        return Result.success(dailyPlanService.getTodayPlan(user));
    }
}
