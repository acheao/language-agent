package com.acheao.languageagent.service;

import com.acheao.languageagent.repository.MaterialStatsRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class StatsService {

    private final MaterialStatsRepository materialStatsRepository;

    public StatsService(MaterialStatsRepository materialStatsRepository) {
        this.materialStatsRepository = materialStatsRepository;
    }

    public Map<String, Object> getOverview() {
        long totalMaterials = materialStatsRepository.count();
        // MVP: Simple count aggregation for now
        return Map.of(
                "totalMaterialsCount", totalMaterials,
                "practicedMaterialsCount", totalMaterials > 0 ? (totalMaterials / 2) : 0 // Mock stat for MVP
        );
    }
}
