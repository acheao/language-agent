package com.acheao.languageagent.service;

import com.acheao.languageagent.entity.Material;
import com.acheao.languageagent.entity.MaterialStats;
import com.acheao.languageagent.repository.MaterialRepository;
import com.acheao.languageagent.repository.MaterialStatsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SchedulerService {

    private final MaterialStatsRepository materialStatsRepository;
    private final MaterialRepository materialRepository;

    public SchedulerService(MaterialStatsRepository materialStatsRepository, MaterialRepository materialRepository) {
        this.materialStatsRepository = materialStatsRepository;
        this.materialRepository = materialRepository;
    }

    public List<Material> pickMaterials(int batchSize) {
        LocalDateTime now = LocalDateTime.now();
        List<MaterialStats> candidates = materialStatsRepository.findDueMaterials(now);

        // Sorting is mostly handled by the DB query (due first, then by nextReviewAt)
        // For MVP, we'll take top N directly. In V2, apply weakness scores.
        List<MaterialStats> selectedStats = candidates.stream()
                .limit(batchSize)
                .toList();

        return selectedStats.stream()
                .map(stats -> materialRepository.findById(stats.getMaterialId()).orElse(null))
                .filter(m -> m != null)
                .collect(Collectors.toList());
    }
}
