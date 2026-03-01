package com.acheao.languageagent.service;

import com.acheao.languageagent.client.LlmClient;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MaterialService {

    private static final Logger log = LoggerFactory.getLogger(MaterialService.class);

    private final MaterialRepository materialRepository;
    private final MaterialStatsRepository materialStatsRepository;
    private final LlmClient llmClient;

    public MaterialService(
            MaterialRepository materialRepository,
            MaterialStatsRepository materialStatsRepository,
            LlmClient llmClient) {
        this.materialRepository = materialRepository;
        this.materialStatsRepository = materialStatsRepository;
        this.llmClient = llmClient;
    }

    @Transactional
    public ImportResult importMaterials(MaterialImportRequest request) {
        List<String> rawLines = request.getLines() == null ? List.of() : request.getLines();
        List<String> validLines = rawLines.stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        boolean autoType = isAutoType(request.getType());
        String fixedType = normalizeFixedType(request.getType());

        List<Material> savedMaterials = new ArrayList<>();
        int skippedDuplicates = 0;
        Set<String> seenInRequest = new LinkedHashSet<>();

        for (String line : validLines) {
            List<ImportSeed> seeds = autoType
                    ? buildAutoImportSeeds(line)
                    : List.of(new ImportSeed(fixedType, line));

            for (ImportSeed seed : seeds) {
                String content = seed.content() == null ? "" : seed.content().trim();
                if (content.isEmpty()) {
                    continue;
                }

                String dedupeKey = content.toLowerCase(Locale.ROOT);
                if (!seenInRequest.add(dedupeKey) || materialRepository.existsByContent(content)) {
                    log.info("Skipping duplicate material: {}", content);
                    skippedDuplicates++;
                    continue;
                }

                Material material = new Material();
                material.setType(seed.type());
                material.setContent(content);
                material.setEnabled(true);
                material.setQuestionGenerated(false);

                Material saved = materialRepository.save(material);
                savedMaterials.add(saved);

                MaterialStats stats = new MaterialStats();
                stats.setMaterialId(saved.getId());
                stats.setNextReviewAt(LocalDateTime.now());
                materialStatsRepository.save(stats);
            }
        }

        log.info("Imported {} materials, skipped {}", savedMaterials.size(), skippedDuplicates);
        return new ImportResult(rawLines.size(), savedMaterials.size(), skippedDuplicates);
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

    private boolean isAutoType(String type) {
        return type != null && "auto".equalsIgnoreCase(type.trim());
    }

    private String normalizeFixedType(String type) {
        if (type == null || type.isBlank() || isAutoType(type)) {
            return "unknown";
        }
        return type.trim().toLowerCase(Locale.ROOT);
    }

    private List<ImportSeed> buildAutoImportSeeds(String line) {
        try {
            LlmClient.MaterialAutoParseResult auto = llmClient.analyzeAndSplitMaterialAuto(line);
            String type = normalizeAutoType(auto.getType(), line);
            List<String> segments = auto.getSegments() == null ? List.of() : auto.getSegments();
            if (segments.isEmpty()) {
                return List.of(new ImportSeed(type, line));
            }

            if (!"sentence".equals(type)) {
                return List.of(new ImportSeed(type, segments.getFirst()));
            }

            return segments.stream()
                    .map(segment -> new ImportSeed("sentence", segment))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Auto import parse failed for line, using fallback type inference. reason={}", e.getMessage());
            return List.of(new ImportSeed(inferTypeFallback(line), line));
        }
    }

    private String normalizeAutoType(String parsedType, String source) {
        if (parsedType != null && !parsedType.isBlank()) {
            String normalized = parsedType.trim().toLowerCase(Locale.ROOT);
            if ("word".equals(normalized) || "phrase".equals(normalized) || "sentence".equals(normalized)) {
                return normalized;
            }
        }
        return inferTypeFallback(source);
    }

    private String inferTypeFallback(String source) {
        if (source == null || source.isBlank()) {
            return "unknown";
        }
        String normalized = source.trim();
        long tokenCount = List.of(normalized.split("\\s+")).stream()
                .filter(token -> !token.isBlank())
                .count();
        if (tokenCount <= 1) {
            return "word";
        }
        if (tokenCount <= 4 && !normalized.matches(".*[.!?]$")) {
            return "phrase";
        }
        return "sentence";
    }

    private record ImportSeed(String type, String content) {
    }

    public static class ImportResult {
        private final int totalProvided;
        private final int successfullyImported;
        private final int skippedDuplicates;

        public ImportResult(int totalProvided, int successfullyImported, int skippedDuplicates) {
            this.totalProvided = totalProvided;
            this.successfullyImported = successfullyImported;
            this.skippedDuplicates = skippedDuplicates;
        }

        public int getTotalProvided() {
            return totalProvided;
        }

        public int getSuccessfullyImported() {
            return successfullyImported;
        }

        public int getSkippedDuplicates() {
            return skippedDuplicates;
        }
    }
}
