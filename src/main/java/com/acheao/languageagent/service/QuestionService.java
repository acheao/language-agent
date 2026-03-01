package com.acheao.languageagent.service;

import com.acheao.languageagent.client.LlmClient;
import com.acheao.languageagent.entity.Material;
import com.acheao.languageagent.entity.Question;
import com.acheao.languageagent.exception.LlmApiException;
import com.acheao.languageagent.repository.QuestionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    private static final String FALLBACK_MODEL = "fallback_template";

    private final QuestionRepository questionRepository;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public QuestionService(QuestionRepository questionRepository, LlmClient llmClient, ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    // Backward-compatible entry.
    public List<Question> generateQuestions(UUID sessionId, List<Material> materials, String mode) {
        return generateNewQuestions(sessionId, materials, isTranslationOnlyMode(mode));
    }

    // New material mode:
    // 1) sentence material -> translation model directly
    // 2) phrase/word material -> deepseek simple sentence + translation model
    public List<Question> generateNewQuestions(UUID sessionId, List<Material> materials, boolean translationOnly) {
        List<Question> questions = new ArrayList<>();

        for (Material material : materials) {
            boolean firstGenerationForMaterial = !questionRepository.existsByMaterialId(material.getId());

            try {
                questions.add(buildNewQuestionByRules(sessionId, material));
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
                } catch (JsonProcessingException jsonException) {
                    throw new IllegalStateException("Failed to serialize fallback new-question payload",
                            jsonException);
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize new-question payload", e);
            }
        }

        return questionRepository.saveAll(questions);
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

        LlmClient.SimpleSentenceResult generatedSentence = llmClient.generateSimpleSentenceFromMaterial(
                material.getType(),
                material.getContent());
        answerSentence = normalizeSentence(generatedSentence.getSentence());
        LlmClient.TranslationResult translationResult = llmClient.translateEnglishToChinese(answerSentence);
        modelTrace = generatedSentence.getModel() + " -> " + translationResult.getModel();
        return buildTranslationQuestion(
                sessionId,
                material,
                translationResult.getTranslation(),
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
        LlmClient.QuestionGenerationResult llmResult = llmClient.generateErrorFocusedSentenceWritingQuestion(
                context.getMaterial().getType(),
                context.getMaterial().getContent(),
                context.getPrimaryErrorType(),
                buildErrorContextText(context));

        Question question = new Question();
        question.setSessionId(sessionId);
        question.setMaterialId(context.getMaterial().getId());
        question.setType(TYPE_ERROR_TARGETED_SENTENCE_WRITING);
        question.setPrompt(llmResult.getResponse().getPrompt());
        question.setReferenceAnswer(objectMapper.writeValueAsString(Map.of(
                "text", normalizeSentence(llmResult.getResponse().getReferenceSentence()))));
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
        String prompt = String.format(
                "Write one English sentence using this material and focus on %s: %s",
                safe(context.getPrimaryErrorType(), "grammar"),
                context.getMaterial().getContent());

        String referenceSentence = context.getCorrectedAnswer();
        if (referenceSentence == null || referenceSentence.isBlank()) {
            referenceSentence = buildFallbackReferenceSentence(context.getMaterial());
        }

        Question question = new Question();
        question.setSessionId(sessionId);
        question.setMaterialId(context.getMaterial().getId());
        question.setType(TYPE_ERROR_TARGETED_SENTENCE_WRITING);
        question.setPrompt(prompt);
        question.setReferenceAnswer(objectMapper.writeValueAsString(Map.of("text", normalizeSentence(referenceSentence))));
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
