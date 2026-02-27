package com.acheao.languageagent.service;

import com.acheao.languageagent.dto.MaterialImportRequest;
import com.acheao.languageagent.entity.Material;
import com.acheao.languageagent.entity.MaterialStats;
import com.acheao.languageagent.exception.ResourceNotFoundException;
import com.acheao.languageagent.repository.MaterialRepository;
import com.acheao.languageagent.repository.MaterialStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MaterialService {

    private static final Logger log = LoggerFactory.getLogger(MaterialService.class);

    private final MaterialRepository materialRepository;
    private final MaterialStatsRepository materialStatsRepository;

    public MaterialService(MaterialRepository materialRepository, MaterialStatsRepository materialStatsRepository) {
        this.materialRepository = materialRepository;
        this.materialStatsRepository = materialStatsRepository;
    }

    @Transactional
    public ImportResult importMaterials(MaterialImportRequest request) {
        List<String> validLines = request.getLines().stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        List<Material> savedMaterials = new ArrayList<>();

        for (String line : validLines) {
            Material material = new Material();
            material.setType(request.getType() != null ? request.getType() : "unknown");
            material.setContent(line);
            material.setEnabled(true);

            Material saved = materialRepository.save(material);
            savedMaterials.add(saved);

            MaterialStats stats = new MaterialStats();
            stats.setMaterialId(saved.getId());
            stats.setNextReviewAt(LocalDateTime.now());
            materialStatsRepository.save(stats);
        }

        log.info("Imported {} materials", savedMaterials.size());
        return new ImportResult(request.getLines().size(), savedMaterials.size());
    }

    public List<Material> getAllMaterials() {
        return materialRepository.findAll();
    }

    @Transactional
    public Material updateMaterialEnabled(UUID id, boolean enabled) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Material not found: " + id));
        material.setEnabled(enabled);
        return materialRepository.save(material);
    }

    public static class ImportResult {
        private final int totalProvided;
        private final int successfullyImported;

        public ImportResult(int totalProvided, int successfullyImported) {
            this.totalProvided = totalProvided;
            this.successfullyImported = successfullyImported;
        }

        public int getTotalProvided() {
            return totalProvided;
        }

        public int getSuccessfullyImported() {
            return successfullyImported;
        }
    }
}
