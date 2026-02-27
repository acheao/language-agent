package com.acheao.languageagent.controller;

import com.acheao.languageagent.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "Statistics", description = "Endpoints for viewing learning progress")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Get overview statistics", description = "Returns high-level training counts")
    public ResponseEntity<Map<String, Object>> getOverview() {
        return ResponseEntity.ok(statsService.getOverview());
    }

    @GetMapping("/error-types")
    @Operation(summary = "Get error type statistics", description = "Returns frequency matrix for specific errors (MVP static placeholder)")
    public ResponseEntity<Map<String, Object>> getErrorTypes() {
        // Mock error frequency distribution
        return ResponseEntity.ok(Map.of(
                "grammar_mistakes", 14,
                "spelling", 5,
                "vocab_choice", 23));
    }
}
