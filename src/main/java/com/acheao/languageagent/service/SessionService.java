package com.acheao.languageagent.service;

import com.acheao.languageagent.entity.Material;
import com.acheao.languageagent.entity.MaterialStats;
import com.acheao.languageagent.entity.Question;
import com.acheao.languageagent.entity.Session;
import com.acheao.languageagent.repository.GradingRepository;
import com.acheao.languageagent.repository.MaterialRepository;
import com.acheao.languageagent.repository.MaterialStatsRepository;
import com.acheao.languageagent.repository.QuestionRepository;
import com.acheao.languageagent.repository.SessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessionRepository;
    private final SchedulerService schedulerService;
    private final QuestionService questionService;
    private final QuestionRepository questionRepository;
    private final MaterialRepository materialRepository;
    private final MaterialStatsRepository materialStatsRepository;
    private final GradingRepository gradingRepository;
    private final ObjectMapper objectMapper;

    public SessionService(
            SessionRepository sessionRepository,
            SchedulerService schedulerService,
            QuestionService questionService,
            QuestionRepository questionRepository,
            MaterialRepository materialRepository,
            MaterialStatsRepository materialStatsRepository,
            GradingRepository gradingRepository,
            ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.schedulerService = schedulerService;
        this.questionService = questionService;
        this.questionRepository = questionRepository;
        this.materialRepository = materialRepository;
        this.materialStatsRepository = materialStatsRepository;
        this.gradingRepository = gradingRepository;
        this.objectMapper = objectMapper;
    }

    public SessionResult createSessionAndQuestions(int batchSize, String generatorMode) {
        Session session = new Session();
        session.setBatchSize(batchSize);
        session.setGeneratorMode(generatorMode);
        Session savedSession = sessionRepository.save(session);

        Mode mode = Mode.from(generatorMode);
        boolean translationOnly = isTranslationOnlyMode(generatorMode);

        List<Question> questions = switch (mode) {
            case NEW -> generateNewModeQuestions(savedSession.getId(), batchSize, translationOnly);
            case WRONG -> generateWrongModeQuestions(savedSession.getId(), batchSize);
            case REVIEW -> generateReviewModeQuestions(savedSession.getId(), batchSize);
            case SMART -> generateSmartModeQuestions(savedSession.getId(), batchSize);
        };

        return new SessionResult(savedSession, questions);
    }

    private List<Question> generateNewModeQuestions(UUID sessionId, int batchSize, boolean translationOnly) {
        List<Material> materials = schedulerService.pickNewMaterials(batchSize);
        return questionService.generateNewQuestions(sessionId, materials, translationOnly);
    }

    private List<Question> generateWrongModeQuestions(UUID sessionId, int batchSize) {
        List<QuestionRepository.WrongAttemptProjection> attempts = questionRepository.findLatestWrongAttempts(
                batchSize * 8);
        if (attempts.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> errorTypePriority = buildErrorTypePriorityMap();
        List<WrongAttemptCandidate> rankedCandidates = attempts.stream()
                .map(attempt -> toCandidate(attempt, errorTypePriority))
                .sorted(Comparator
                        .comparingInt(WrongAttemptCandidate::priorityScore).reversed()
                        .thenComparing(WrongAttemptCandidate::wrongAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Map<UUID, QuestionService.WrongQuestionContext> contextByMaterial = new LinkedHashMap<>();
        for (WrongAttemptCandidate candidate : rankedCandidates) {
            UUID materialId = candidate.attempt().getMaterialId();
            if (materialId == null || contextByMaterial.containsKey(materialId)) {
                continue;
            }

            Material material = materialRepository.findById(materialId).orElse(null);
            if (material == null || !material.isEnabled()) {
                continue;
            }

            QuestionService.WrongQuestionContext context = new QuestionService.WrongQuestionContext();
            context.setMaterial(material);
            context.setSourceQuestionType(candidate.attempt().getQuestionType());
            context.setPrimaryErrorType(candidate.primaryErrorType());
            context.setUserAnswer(candidate.attempt().getUserAnswer());
            context.setCorrectedAnswer(candidate.attempt().getCorrectedAnswer());
            context.setErrorTypes(candidate.attempt().getErrorTypes());
            context.setExplanationZh(candidate.attempt().getExplanationZh());
            contextByMaterial.put(materialId, context);

            if (contextByMaterial.size() >= batchSize) {
                break;
            }
        }

        return questionService.regenerateWrongQuestions(sessionId, new ArrayList<>(contextByMaterial.values()));
    }

    private List<Question> generateReviewModeQuestions(UUID sessionId, int batchSize) {
        List<QuestionRepository.WrongAttemptProjection> attempts = questionRepository.findLatestWrongAttempts(
                Math.max(batchSize * 10, 30));
        if (attempts.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        Map<UUID, QuestionRepository.WrongAttemptProjection> latestAttemptByQuestion = new LinkedHashMap<>();
        for (QuestionRepository.WrongAttemptProjection attempt : attempts) {
            UUID questionId = attempt.getQuestionId();
            if (questionId == null || latestAttemptByQuestion.containsKey(questionId)) {
                continue;
            }
            latestAttemptByQuestion.put(questionId, attempt);
        }

        List<ReviewQuestionCandidate> candidates = new ArrayList<>();
        for (Map.Entry<UUID, QuestionRepository.WrongAttemptProjection> entry : latestAttemptByQuestion.entrySet()) {
            Question question = questionRepository.findById(entry.getKey()).orElse(null);
            if (question == null) {
                continue;
            }

            MaterialStats stats = null;
            UUID materialId = question.getMaterialId();
            if (materialId != null) {
                Material material = materialRepository.findById(materialId).orElse(null);
                if (material == null || !material.isEnabled()) {
                    continue;
                }
                stats = materialStatsRepository.findById(materialId).orElse(null);
            }

            double reviewValue = scoreReviewValue(entry.getValue(), stats, now);
            candidates.add(new ReviewQuestionCandidate(question, reviewValue));
        }

        List<Question> selected = candidates.stream()
                .sorted(Comparator.comparingDouble(ReviewQuestionCandidate::score).reversed())
                .limit(batchSize)
                .map(ReviewQuestionCandidate::question)
                .toList();
        return questionService.reuseWrongQuestions(sessionId, selected);
    }

    private List<Question> generateSmartModeQuestions(UUID sessionId, int batchSize) {
        int poolSize = Math.max(batchSize * 8, 30);
        LocalDateTime now = LocalDateTime.now();
        Set<UUID> recentlyUsedMaterialIds = new LinkedHashSet<>(
                questionRepository.findDistinctMaterialIdsSince(now.minusHours(12)));

        List<QuestionRepository.WrongAttemptProjection> wrongAttempts = questionRepository.findLatestWrongAttempts(
                poolSize);
        Map<UUID, QuestionRepository.WrongAttemptProjection> latestWrongByMaterial = buildLatestWrongAttemptByMaterial(
                wrongAttempts);

        List<Material> dueMaterials = schedulerService.pickMaterials(Math.max(batchSize * 4, batchSize));
        List<Material> newMaterials = schedulerService.pickNewMaterials(Math.max(batchSize * 4, batchSize));
        Map<UUID, Material> candidateMaterials = buildCandidateMaterialMap(
                dueMaterials,
                newMaterials,
                latestWrongByMaterial);
        if (candidateMaterials.isEmpty()) {
            return List.of();
        }

        Map<UUID, MaterialStats> statsByMaterial = materialStatsRepository.findAllById(candidateMaterials.keySet())
                .stream()
                .collect(Collectors.toMap(MaterialStats::getMaterialId, stats -> stats));
        Map<String, Integer> errorTypePriority = buildErrorTypePriorityMap();

        List<SmartCandidate> rankedCandidates = candidateMaterials.values().stream()
                .map(material -> buildSmartCandidate(material, latestWrongByMaterial.get(material.getId()),
                        statsByMaterial.get(material.getId()), errorTypePriority, recentlyUsedMaterialIds, now))
                .sorted(Comparator.comparingDouble(SmartCandidate::score).reversed())
                .toList();

        List<SmartCandidate> distinctCandidates = deduplicateCandidatesByContent(rankedCandidates);

        List<Question> generatedQuestions = new ArrayList<>();
        for (SmartCandidate candidate : distinctCandidates) {
            if (generatedQuestions.size() >= batchSize) {
                break;
            }
            try {
                generateOneSmartQuestion(sessionId, candidate, generatedQuestions);
            } catch (Exception e) {
                log.warn("Smart mode failed for material {}. reason={}", candidate.material().getId(), e.getMessage());
            }
        }

        return generatedQuestions;
    }

    private List<SmartCandidate> deduplicateCandidatesByContent(List<SmartCandidate> rankedCandidates) {
        Map<String, SmartCandidate> uniqueByContent = new LinkedHashMap<>();
        for (SmartCandidate candidate : rankedCandidates) {
            String signature = normalizeContent(candidate.material().getContent());
            if (signature.isBlank()) {
                continue;
            }
            uniqueByContent.putIfAbsent(signature, candidate);
        }
        return new ArrayList<>(uniqueByContent.values());
    }

    private Map<UUID, QuestionRepository.WrongAttemptProjection> buildLatestWrongAttemptByMaterial(
            List<QuestionRepository.WrongAttemptProjection> attempts) {
        Map<UUID, QuestionRepository.WrongAttemptProjection> latest = new LinkedHashMap<>();
        for (QuestionRepository.WrongAttemptProjection attempt : attempts) {
            UUID materialId = attempt.getMaterialId();
            if (materialId == null || latest.containsKey(materialId)) {
                continue;
            }
            latest.put(materialId, attempt);
        }
        return latest;
    }

    private Map<UUID, Material> buildCandidateMaterialMap(
            List<Material> dueMaterials,
            List<Material> newMaterials,
            Map<UUID, QuestionRepository.WrongAttemptProjection> latestWrongByMaterial) {
        Map<UUID, Material> candidates = new LinkedHashMap<>();

        for (Material material : dueMaterials) {
            if (material != null && material.isEnabled() && hasUsableMaterialContent(material)) {
                candidates.put(material.getId(), material);
            }
        }
        for (Material material : newMaterials) {
            if (material != null && material.isEnabled() && hasUsableMaterialContent(material)) {
                candidates.put(material.getId(), material);
            }
        }

        Set<UUID> missingIds = new LinkedHashSet<>(latestWrongByMaterial.keySet());
        missingIds.removeAll(candidates.keySet());
        if (!missingIds.isEmpty()) {
            for (Material material : materialRepository.findAllById(missingIds)) {
                if (material.isEnabled() && hasUsableMaterialContent(material)) {
                    candidates.put(material.getId(), material);
                }
            }
        }

        return candidates;
    }

    private SmartCandidate buildSmartCandidate(
            Material material,
            QuestionRepository.WrongAttemptProjection latestWrong,
            MaterialStats stats,
            Map<String, Integer> errorTypePriority,
            Set<UUID> recentlyUsedMaterialIds,
            LocalDateTime now) {
        List<String> parsedErrorTypes = latestWrong == null
                ? List.of()
                : parseErrorTypes(latestWrong.getErrorTypes());
        String primaryErrorType = pickPrimaryErrorType(parsedErrorTypes, errorTypePriority);
        SmartQuestionStrategy strategy = decideSmartStrategy(latestWrong, now);
        double score = computeSmartScore(
                material.getId(),
                stats,
                latestWrong,
                primaryErrorType,
                errorTypePriority,
                recentlyUsedMaterialIds,
                now);

        QuestionService.WrongQuestionContext wrongContext = null;
        if (latestWrong != null) {
            wrongContext = new QuestionService.WrongQuestionContext();
            wrongContext.setMaterial(material);
            wrongContext.setSourceQuestionType(latestWrong.getQuestionType());
            wrongContext.setPrimaryErrorType(primaryErrorType);
            wrongContext.setUserAnswer(latestWrong.getUserAnswer());
            wrongContext.setCorrectedAnswer(latestWrong.getCorrectedAnswer());
            wrongContext.setErrorTypes(latestWrong.getErrorTypes());
            wrongContext.setExplanationZh(latestWrong.getExplanationZh());
        }

        return new SmartCandidate(
                material,
                score,
                strategy,
                wrongContext,
                latestWrong == null ? null : latestWrong.getQuestionId());
    }

    private void generateOneSmartQuestion(UUID sessionId, SmartCandidate candidate, List<Question> collector) {
        switch (candidate.strategy()) {
            case REVIEW_REUSE -> {
                UUID questionId = candidate.sourceQuestionId();
                if (questionId != null) {
                    Question source = questionRepository.findById(questionId).orElse(null);
                    if (source != null) {
                        collector.addAll(questionService.reuseWrongQuestions(sessionId, List.of(source)));
                        return;
                    }
                }
                if (candidate.wrongContext() != null) {
                    collector.addAll(questionService.regenerateWrongQuestions(sessionId, List.of(candidate.wrongContext())));
                    return;
                }
                collector.addAll(questionService.generateNewQuestions(sessionId, List.of(candidate.material()), false));
            }
            case WRONG_REGENERATE -> {
                if (candidate.wrongContext() != null) {
                    collector.addAll(questionService.regenerateWrongQuestions(sessionId, List.of(candidate.wrongContext())));
                } else {
                    collector.addAll(questionService.generateNewQuestions(sessionId, List.of(candidate.material()), false));
                }
            }
            case NEW -> collector.addAll(questionService.generateNewQuestions(sessionId, List.of(candidate.material()), false));
        }
    }

    private double computeSmartScore(
            UUID materialId,
            MaterialStats stats,
            QuestionRepository.WrongAttemptProjection latestWrong,
            String primaryErrorType,
            Map<String, Integer> errorTypePriority,
            Set<UUID> recentlyUsedMaterialIds,
            LocalDateTime now) {
        int practiceCount = stats == null ? 0 : stats.getPracticeCount();
        int correctCount = stats == null ? 0 : stats.getCorrectCount();
        int wrongCount = Math.max(0, practiceCount - correctCount);

        double errorFrequencyScore = practiceCount == 0 ? 0.0 : (double) wrongCount / practiceCount;
        double score = 4.0 * errorFrequencyScore;
        score += 1.2 * Math.log1p(wrongCount);

        score += 0.9 * timeGapScore(stats, now);
        score += 1.0 * dueScore(stats, now);
        score += 1.1 * errorTypePriorityScore(primaryErrorType, errorTypePriority);
        score += newMaterialBoost(stats);
        score += recentWrongBoost(latestWrong, now);
        score += recentQuestionPenalty(materialId, recentlyUsedMaterialIds);

        return score;
    }

    private double scoreReviewValue(
            QuestionRepository.WrongAttemptProjection wrongAttempt,
            MaterialStats stats,
            LocalDateTime now) {
        double score = 0.0;

        if (stats == null || stats.getLastPracticedAt() == null) {
            score += 1.0;
        } else {
            double hoursSincePractice = Math.max(0.0, Duration.between(stats.getLastPracticedAt(), now).toHours());
            if (hoursSincePractice < 2) {
                score -= 1.4;
            } else {
                score += Math.min(hoursSincePractice / 24.0, 2.0);
            }
        }

        if (stats == null || stats.getNextReviewAt() == null) {
            score += 0.5;
        } else if (!stats.getNextReviewAt().isAfter(now)) {
            score += 1.6;
        } else {
            double hoursUntilDue = Math.max(0.0, Duration.between(now, stats.getNextReviewAt()).toHours());
            score += Math.max(0.0, 1.0 - (hoursUntilDue / 48.0));
        }

        if (wrongAttempt != null && wrongAttempt.getWrongAt() != null) {
            double hoursSinceWrong = Math.max(0.0, Duration.between(wrongAttempt.getWrongAt(), now).toHours());
            if (hoursSinceWrong < 8) {
                score += 0.3;
            } else if (hoursSinceWrong <= 72) {
                score += 1.0;
            } else {
                score += 0.6;
            }
        }

        return score;
    }

    private double timeGapScore(MaterialStats stats, LocalDateTime now) {
        if (stats == null || stats.getLastPracticedAt() == null) {
            return 1.0;
        }
        double days = Math.max(0.0, Duration.between(stats.getLastPracticedAt(), now).toHours() / 24.0);
        return Math.min(days / 3.0, 1.5);
    }

    private double dueScore(MaterialStats stats, LocalDateTime now) {
        if (stats == null || stats.getNextReviewAt() == null) {
            return 1.0;
        }
        return stats.getNextReviewAt().isAfter(now) ? 0.0 : 1.2;
    }

    private double errorTypePriorityScore(String errorType, Map<String, Integer> priorityMap) {
        if (priorityMap.isEmpty()) {
            return 0.0;
        }
        int maxPriority = priorityMap.values().stream().max(Integer::compareTo).orElse(1);
        int value = priorityMap.getOrDefault(normalizeErrorType(errorType), 0);
        return (double) value / maxPriority;
    }

    private double newMaterialBoost(MaterialStats stats) {
        if (stats == null || stats.getPracticeCount() == 0) {
            return 0.8;
        }
        return 0.0;
    }

    private double recentWrongBoost(QuestionRepository.WrongAttemptProjection latestWrong, LocalDateTime now) {
        if (latestWrong == null || latestWrong.getWrongAt() == null) {
            return 0.0;
        }

        double hours = Math.max(0.0, Duration.between(latestWrong.getWrongAt(), now).toHours());
        if (hours <= 8) {
            return 0.4;
        }
        if (hours <= 48) {
            return 0.9;
        }
        return 0.6;
    }

    private double recentQuestionPenalty(UUID materialId, Set<UUID> recentlyUsedMaterialIds) {
        if (materialId == null) {
            return 0.0;
        }
        return recentlyUsedMaterialIds.contains(materialId) ? -1.6 : 0.0;
    }

    private SmartQuestionStrategy decideSmartStrategy(
            QuestionRepository.WrongAttemptProjection latestWrong,
            LocalDateTime now) {
        if (latestWrong == null) {
            return SmartQuestionStrategy.NEW;
        }
        if (latestWrong.getWrongAt() == null) {
            return SmartQuestionStrategy.WRONG_REGENERATE;
        }
        long hoursSinceWrong = Duration.between(latestWrong.getWrongAt(), now).toHours();
        if (hoursSinceWrong <= 12) {
            return SmartQuestionStrategy.REVIEW_REUSE;
        }
        return SmartQuestionStrategy.WRONG_REGENERATE;
    }

    private Map<String, Integer> buildErrorTypePriorityMap() {
        List<GradingRepository.ErrorTypeRankProjection> rankRows = gradingRepository.findTopWrongErrorTypes(50);
        Map<String, Integer> priority = new HashMap<>();
        int size = rankRows.size();

        for (int i = 0; i < rankRows.size(); i++) {
            String normalized = normalizeErrorType(rankRows.get(i).getErrorType());
            if (normalized.isEmpty()) {
                continue;
            }
            // Higher ranked error type gets larger score.
            priority.put(normalized, size - i);
        }
        return priority;
    }

    private WrongAttemptCandidate toCandidate(
            QuestionRepository.WrongAttemptProjection attempt,
            Map<String, Integer> errorTypePriority) {
        List<String> parsedTypes = parseErrorTypes(attempt.getErrorTypes());
        String primaryErrorType = pickPrimaryErrorType(parsedTypes, errorTypePriority);
        int score = scoreByErrorTypes(parsedTypes, errorTypePriority);
        return new WrongAttemptCandidate(attempt, score, primaryErrorType);
    }

    private int scoreByErrorTypes(List<String> errorTypes, Map<String, Integer> priorityMap) {
        if (errorTypes.isEmpty()) {
            return 0;
        }
        int max = 0;
        for (String type : errorTypes) {
            int score = priorityMap.getOrDefault(normalizeErrorType(type), 0);
            if (score > max) {
                max = score;
            }
        }
        return max;
    }

    private String pickPrimaryErrorType(List<String> errorTypes, Map<String, Integer> priorityMap) {
        if (errorTypes.isEmpty()) {
            return "grammar";
        }

        String selected = errorTypes.getFirst();
        int bestScore = priorityMap.getOrDefault(normalizeErrorType(selected), 0);

        for (String type : errorTypes) {
            int score = priorityMap.getOrDefault(normalizeErrorType(type), 0);
            if (score > bestScore) {
                bestScore = score;
                selected = type;
            }
        }

        return selected;
    }

    private List<String> parseErrorTypes(String errorTypesJson) {
        if (errorTypesJson == null || errorTypesJson.isBlank()) {
            return List.of();
        }

        try {
            List<String> parsed = objectMapper.readValue(errorTypesJson, new TypeReference<List<String>>() {
            });
            return parsed == null
                    ? List.of()
                    : parsed.stream().filter(type -> type != null && !type.isBlank()).toList();
        } catch (Exception e) {
            log.debug("Failed to parse error types JSON: {}", errorTypesJson);
            return List.of();
        }
    }

    private String normalizeErrorType(String errorType) {
        if (errorType == null) {
            return "";
        }
        return errorType.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasUsableMaterialContent(Material material) {
        return !normalizeContent(material.getContent()).isBlank();
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private enum Mode {
        NEW,
        WRONG,
        REVIEW,
        SMART;

        static Mode from(String raw) {
            if (raw == null || raw.isBlank()) {
                return NEW;
            }

            String mode = raw.trim().toLowerCase(Locale.ROOT);
            String rawTrim = raw.trim();
            if ("wrong".equals(mode) || "mistake".equals(mode) || "\u9519\u9898".equals(rawTrim)) {
                return WRONG;
            }
            if ("review".equals(mode) || "revision".equals(mode) || "\u590d\u4e60".equals(rawTrim)) {
                return REVIEW;
            }
            if ("smart".equals(mode)
                    || "intelligent".equals(mode)
                    || "adaptive".equals(mode)
                    || "auto".equals(mode)
                    || "smart_mode".equals(mode)
                    || "smart-mode".equals(mode)
                    || "\u667a\u80fd".equals(rawTrim)) {
                return SMART;
            }
            return NEW;
        }
    }

    private enum SmartQuestionStrategy {
        NEW,
        WRONG_REGENERATE,
        REVIEW_REUSE
    }

    private boolean isTranslationOnlyMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String mode = raw.trim().toLowerCase(Locale.ROOT);
        return "translation".equals(mode)
                || "translate".equals(mode)
                || "translation_only".equals(mode)
                || "translation-only".equals(mode)
                || "new_translation".equals(mode)
                || "new-translation".equals(mode);
    }

    private record WrongAttemptCandidate(
            QuestionRepository.WrongAttemptProjection attempt,
            int priorityScore,
            String primaryErrorType) {
        LocalDateTime wrongAt() {
            return attempt.getWrongAt();
        }
    }

    private record SmartCandidate(
            Material material,
            double score,
            SmartQuestionStrategy strategy,
            QuestionService.WrongQuestionContext wrongContext,
            UUID sourceQuestionId) {
    }

    private record ReviewQuestionCandidate(
            Question question,
            double score) {
    }

    public static class SessionResult {
        private final Session session;
        private final List<Question> questions;

        public SessionResult(Session session, List<Question> questions) {
            this.session = session;
            this.questions = questions;
        }

        public Session getSession() {
            return session;
        }

        public List<Question> getQuestions() {
            return questions;
        }
    }
}
