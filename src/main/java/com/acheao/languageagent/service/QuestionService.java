package com.acheao.languageagent.service;

import com.acheao.languageagent.client.LlmClient;
import com.acheao.languageagent.entity.Material;
import com.acheao.languageagent.entity.Question;
import com.acheao.languageagent.exception.LlmApiException;
import com.acheao.languageagent.repository.MaterialRepository;
import com.acheao.languageagent.repository.QuestionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);

    private static final String TYPE_TRANSLATION_SENTENCE_WRITING = "translation_sentence_writing";
    private static final String TYPE_ERROR_TARGETED_SENTENCE_WRITING = "error_targeted_sentence_writing";
    private static final String TYPE_ERROR_TARGETED_FILL_BLANK = "error_targeted_fill_blank";
    private static final String TYPE_ERROR_TARGETED_CORRECTION = "error_targeted_correction";
    private static final String FALLBACK_MODEL = "fallback_template";

    private final QuestionRepository questionRepository;
    private final MaterialRepository materialRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public QuestionService(
            QuestionRepository questionRepository,
            MaterialRepository materialRepository,
            LlmClient llmClient,
            ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.materialRepository = materialRepository;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    // Backward-compatible entry.
    public List<Question> generateQuestions(UUID sessionId, List<Material> materials, String mode) {
        return generateNewQuestions(sessionId, materials, isTranslationOnlyMode(mode));
    }

    // New material mode:
    // 1) sentence material -> translation model directly (material sentence is answer)
    // 2) phrase/word material -> deepseek one-shot generates Chinese prompt + English answer
    @Transactional
    public List<Question> generateNewQuestions(UUID sessionId, List<Material> materials, boolean translationOnly) {
        List<Question> questions = new ArrayList<>();
        List<Material> generatedMaterials = new ArrayList<>();

        for (Material material : materials) {
            boolean firstGenerationForMaterial = !questionRepository.existsByMaterialId(material.getId());

            try {
                questions.add(buildNewQuestionByRules(sessionId, material));
                generatedMaterials.add(material);
            } catch (LlmApiException e) {
                if (firstGenerationForMaterial) {
                    throw new LlmApiException(
                            "First-time question generation must use LLM and cannot fallback: " + material.getId(),
                            e);
                }

                log.warn("LLM new-question generation failed for material {}, using fallback template. reason={}",
                        material.getId(), e.getMessage());
                try {
                    questions.add(buildFallbackNewQuestion(sessionId, material));
                    generatedMaterials.add(material);
                } catch (JsonProcessingException jsonException) {
                    throw new IllegalStateException("Failed to serialize fallback new-question payload",
                            jsonException);
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize new-question payload", e);
            }
        }

        List<Question> saved = questionRepository.saveAll(questions);
        markMaterialsAsGenerated(generatedMaterials);
        return saved;
    }

    public List<Question> regenerateWrongQuestions(UUID sessionId, List<WrongQuestionContext> contexts) {
        List<Question> questions = new ArrayList<>();

        for (WrongQuestionContext context : contexts) {
            if (context.getMaterial() == null) {
                continue;
            }

            try {
                questions.add(buildErrorTargetedQuestion(sessionId, context));
            } catch (Exception e) {
                log.warn("LLM wrong-question regeneration failed for material {}, fallback enabled. reason={}",
                        context.getMaterial().getId(), e.getMessage());
                try {
                    questions.add(buildFallbackWrongQuestion(sessionId, context));
                } catch (JsonProcessingException jsonException) {
                    throw new IllegalStateException("Failed to serialize fallback wrong-question payload",
                            jsonException);
                }
            }
        }

        return questionRepository.saveAll(questions);
    }

    public List<Question> reuseWrongQuestions(UUID sessionId, List<Question> sourceQuestions) {
        List<Question> questions = new ArrayList<>();

        for (Question source : sourceQuestions) {
            Question copied = new Question();
            copied.setSessionId(sessionId);
            copied.setMaterialId(source.getMaterialId());
            copied.setType(source.getType());
            copied.setPrompt(source.getPrompt());
            copied.setReferenceAnswer(source.getReferenceAnswer());
            copied.setRubric(source.getRubric());
            copied.setDifficulty(source.getDifficulty());
            copied.setTargetErrorTypes(source.getTargetErrorTypes());
            copied.setLlmModel(source.getLlmModel() == null ? "review_reuse" : source.getLlmModel());
            questions.add(copied);
        }

        return questionRepository.saveAll(questions);
    }

    private Question buildNewQuestionByRules(UUID sessionId, Material material)
            throws JsonProcessingException {
        String answerSentence;
        String modelTrace;

        if (isSentenceMaterial(material)) {
            answerSentence = normalizeSentence(material.getContent());
            LlmClient.TranslationResult translationResult = llmClient.translateEnglishToChinese(answerSentence);
            modelTrace = translationResult.getModel();
            return buildTranslationQuestion(
                    sessionId,
                    material,
                    translationResult.getTranslation(),
                    answerSentence,
                    modelTrace);
        }

        LlmClient.QuestionGenerationResult generatedQuestion = llmClient.generateQuestionFromWordOrPhrase(
                material.getType(),
                material.getContent());
        answerSentence = normalizeSentence(generatedQuestion.getResponse().getReferenceSentence());
        modelTrace = generatedQuestion.getModel();
        return buildTranslationQuestion(
                sessionId,
                material,
                generatedQuestion.getResponse().getPrompt(),
                answerSentence,
                modelTrace);
    }

    private Question buildTranslationQuestion(
            UUID sessionId,
            Material material,
            String chinesePromptText,
            String answerSentence,
            String modelTrace) throws JsonProcessingException {
        String prompt = "Translate the following Chinese into one complete English sentence: " + chinesePromptText;

        Question question = new Question();
        question.setSessionId(sessionId);
        question.setMaterialId(material.getId());
        question.setType(TYPE_TRANSLATION_SENTENCE_WRITING);
        question.setPrompt(prompt);
        question.setReferenceAnswer(objectMapper.writeValueAsString(Map.of("text", answerSentence)));
        question.setRubric(buildRubricJson());
        question.setDifficulty(2);
        question.setLlmModel(modelTrace);
        return question;
    }

    private Question buildErrorTargetedQuestion(UUID sessionId, WrongQuestionContext context)
            throws JsonProcessingException {
        ErrorExerciseType exerciseType = decideErrorExerciseType(context.getPrimaryErrorType());
        LlmClient.QuestionGenerationResult llmResult = llmClient.generateErrorFocusedQuestion(
                context.getMaterial().getType(),
                context.getMaterial().getContent(),
                context.getPrimaryErrorType(),
                buildErrorContextText(context),
                exerciseType.getPromptMode());

        Question question = new Question();
        question.setSessionId(sessionId);
        question.setMaterialId(context.getMaterial().getId());
        question.setType(exerciseType.getQuestionType());
        question.setPrompt(llmResult.getResponse().getPrompt());
        question.setReferenceAnswer(objectMapper.writeValueAsString(Map.of("text",
                normalizeAnswerText(llmResult.getResponse().getReferenceSentence(),
                        buildFallbackReferenceByExerciseType(context.getMaterial(), exerciseType)))));
        question.setRubric(buildRubricJson());
        question.setDifficulty(llmResult.getResponse().getDifficulty());
        question.setTargetErrorTypes(context.getErrorTypes());
        question.setLlmModel(llmResult.getModel());
        return question;
    }

    private Question buildFallbackNewQuestion(UUID sessionId, Material material) throws JsonProcessingException {
        String fallbackSentence = buildFallbackReferenceSentence(material);
        Question question = new Question();
        question.setSessionId(sessionId);
        question.setMaterialId(material.getId());
        question.setType(TYPE_TRANSLATION_SENTENCE_WRITING);
        question.setPrompt("Translate the following Chinese into one complete English sentence: " + material.getContent());
        question.setReferenceAnswer(objectMapper.writeValueAsString(Map.of("text", fallbackSentence)));
        question.setRubric(buildRubricJson());
        question.setDifficulty(2);
        question.setLlmModel(FALLBACK_MODEL);
        return question;
    }

    private Question buildFallbackWrongQuestion(UUID sessionId, WrongQuestionContext context)
            throws JsonProcessingException {
        ErrorExerciseType exerciseType = decideErrorExerciseType(context.getPrimaryErrorType());
        String prompt = buildFallbackWrongPrompt(context, exerciseType);

        String referenceSentence = normalizeAnswerText(
                context.getCorrectedAnswer(),
                buildFallbackReferenceByExerciseType(context.getMaterial(), exerciseType));

        Question question = new Question();
        question.setSessionId(sessionId);
        question.setMaterialId(context.getMaterial().getId());
        question.setType(exerciseType.getQuestionType());
        question.setPrompt(prompt);
        question.setReferenceAnswer(objectMapper.writeValueAsString(Map.of("text", referenceSentence)));
        question.setRubric(buildRubricJson());
        question.setDifficulty(3);
        question.setTargetErrorTypes(context.getErrorTypes());
        question.setLlmModel(FALLBACK_MODEL);
        return question;
    }

    private String buildRubricJson() throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "taskCompletion", 0.35,
                "fluency", 0.2,
                "grammar", 0.25,
                "spellingAndWordForm", 0.2));
    }

    private String buildFallbackReferenceSentence(Material material) {
        String content = material.getContent() == null ? "" : material.getContent().trim();
        if (content.isEmpty()) {
            return "I can write a clear and correct English sentence.";
        }

        if (isSentenceMaterial(material)) {
            return normalizeSentence(content);
        }

        return normalizeSentence("I use " + content + " naturally in this sentence");
    }

    private String buildFallbackReferenceByExerciseType(Material material, ErrorExerciseType exerciseType) {
        String content = safe(material == null ? null : material.getContent(), "the target expression");
        return switch (exerciseType) {
            case FILL_BLANK -> content;
            case CORRECTION -> normalizeSentence("I can use " + content + " correctly in context");
            case SENTENCE_WRITING -> buildFallbackReferenceSentence(material);
        };
    }

    private String buildFallbackWrongPrompt(WrongQuestionContext context, ErrorExerciseType exerciseType) {
        String materialContent = safe(context.getMaterial() == null ? null : context.getMaterial().getContent(), "");
        String errorType = safe(context.getPrimaryErrorType(), "grammar");
        return switch (exerciseType) {
            case FILL_BLANK -> String.format(
                    "Fill in the blank with the correct word or phrase (focus: %s): I ___ %s every day.",
                    errorType,
                    materialContent);
            case CORRECTION -> String.format(
                    "Correct the sentence (focus: %s): I use %s goodly in sentence.",
                    errorType,
                    materialContent);
            case SENTENCE_WRITING -> String.format(
                    "Write one English sentence using this material and focus on %s: %s",
                    errorType,
                    materialContent);
        };
    }

    private ErrorExerciseType decideErrorExerciseType(String primaryErrorType) {
        String normalized = normalizeErrorType(primaryErrorType);
        if (normalized.contains("grammar")
                || normalized.contains("tense")
                || normalized.contains("agreement")
                || normalized.contains("article")
                || normalized.contains("preposition")
                || normalized.contains("collocation")
                || normalized.contains("fixed")) {
            return ErrorExerciseType.FILL_BLANK;
        }

        if (normalized.contains("spelling")) {
            return ErrorExerciseType.CORRECTION;
        }

        return ErrorExerciseType.SENTENCE_WRITING;
    }

    private String normalizeErrorType(String errorType) {
        if (errorType == null) {
            return "";
        }
        return errorType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAnswerText(String answer, String fallback) {
        String normalized = answer == null ? "" : answer.trim();
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized;
    }

    private void markMaterialsAsGenerated(List<Material> materials) {
        if (materials == null || materials.isEmpty()) {
            return;
        }

        List<Material> toUpdate = materials.stream()
                .filter(material -> material != null && !material.isQuestionGenerated())
                .toList();
        if (toUpdate.isEmpty()) {
            return;
        }

        for (Material material : toUpdate) {
            material.setQuestionGenerated(true);
        }
        materialRepository.saveAll(toUpdate);
    }

    private String buildErrorContextText(WrongQuestionContext context) {
        return String.format(
                "primaryErrorType=%s; userAnswer=%s; correctedAnswer=%s; errorTypes=%s; explanationZh=%s",
                safe(context.getPrimaryErrorType(), "unknown"),
                safe(context.getUserAnswer(), ""),
                safe(context.getCorrectedAnswer(), ""),
                safe(context.getErrorTypes(), ""),
                safe(context.getExplanationZh(), ""));
    }

    private boolean isSentenceMaterial(Material material) {
        if (material == null) {
            return false;
        }

        String type = material.getType() == null ? "" : material.getType().trim().toLowerCase(Locale.ROOT);
        if (type.contains("sentence")) {
            return true;
        }
        if (type.contains("word") || type.contains("phrase")) {
            return false;
        }

        String content = material.getContent() == null ? "" : material.getContent().trim();
        int tokenCount = (int) Arrays.stream(content.split("\\s+"))
                .filter(token -> !token.isBlank())
                .count();
        return tokenCount >= 5;
    }

    private String normalizeSentence(String sentence) {
        String normalized = sentence == null ? "" : sentence.trim();
        if (normalized.isEmpty()) {
            return "I can write a clear and correct English sentence.";
        }
        if (normalized.endsWith(".") || normalized.endsWith("!") || normalized.endsWith("?")) {
            return normalized;
        }
        return normalized + ".";
    }

    private boolean isTranslationOnlyMode(String mode) {
        if (mode == null) {
            return false;
        }

        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        return "translation".equals(normalized)
                || "translate".equals(normalized)
                || "translation_only".equals(normalized)
                || "translation-only".equals(normalized);
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private enum ErrorExerciseType {
        SENTENCE_WRITING(TYPE_ERROR_TARGETED_SENTENCE_WRITING, "sentence_writing"),
        FILL_BLANK(TYPE_ERROR_TARGETED_FILL_BLANK, "fill_blank"),
        CORRECTION(TYPE_ERROR_TARGETED_CORRECTION, "correction");

        private final String questionType;
        private final String promptMode;

        ErrorExerciseType(String questionType, String promptMode) {
            this.questionType = questionType;
            this.promptMode = promptMode;
        }

        String getQuestionType() {
            return questionType;
        }

        String getPromptMode() {
            return promptMode;
        }
    }

    public static class WrongQuestionContext {
        private Material material;
        private String sourceQuestionType;
        private String primaryErrorType;
        private String userAnswer;
        private String correctedAnswer;
        private String errorTypes;
        private String explanationZh;

        public Material getMaterial() {
            return material;
        }

        public void setMaterial(Material material) {
            this.material = material;
        }

        public String getSourceQuestionType() {
            return sourceQuestionType;
        }

        public void setSourceQuestionType(String sourceQuestionType) {
            this.sourceQuestionType = sourceQuestionType;
        }

        public String getPrimaryErrorType() {
            return primaryErrorType;
        }

        public void setPrimaryErrorType(String primaryErrorType) {
            this.primaryErrorType = primaryErrorType;
        }

        public String getUserAnswer() {
            return userAnswer;
        }

        public void setUserAnswer(String userAnswer) {
            this.userAnswer = userAnswer;
        }

        public String getCorrectedAnswer() {
            return correctedAnswer;
        }

        public void setCorrectedAnswer(String correctedAnswer) {
            this.correctedAnswer = correctedAnswer;
        }

        public String getErrorTypes() {
            return errorTypes;
        }

        public void setErrorTypes(String errorTypes) {
            this.errorTypes = errorTypes;
        }

        public String getExplanationZh() {
            return explanationZh;
        }

        public void setExplanationZh(String explanationZh) {
            this.explanationZh = explanationZh;
        }
    }
}
