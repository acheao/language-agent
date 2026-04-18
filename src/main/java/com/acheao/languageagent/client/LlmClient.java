package com.acheao.languageagent.client;

import com.acheao.languageagent.dto.LlmGradingResponse;
import com.acheao.languageagent.exception.LlmApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.models.deepseek:${llm.model:Pro/deepseek-ai/DeepSeek-V3}}")
    private String deepseekModel;

    @Value("${llm.models.translation:tencent/Hunyuan-MT-7B}")
    private String translationModel;

    @Value("${llm.timeout-ms:20000}")
    private long timeoutMs;

    @Value("${llm.temperature:0.2}")
    private double temperature;

    public LlmClient(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public GradingResult gradeAnswer(String prompt, String referenceAnswer, String rubric, String userAnswer) {
        String systemPrompt = """
                You are an English writing grader.
                Grade the response using the given prompt, reference answer, and rubric.
                Return JSON only with this schema:
                {
                  "score": 0,
                  "isCorrect": true,
                  "correctedAnswer": "correct sentence",
                  "errorTypes": ["grammar", "spelling"],
                  "explanationZh": "Chinese explanation",
                  "suggestions": ["suggestion 1", "suggestion 2"]
                }
                """;

        String userPrompt = String.format("Question: %s%nReference Answer: %s%nRubric: %s%nUser Answer: %s",
                prompt, referenceAnswer, rubric, userAnswer);

        try {
            String content = callJsonChatCompletion(
                    deepseekModel,
                    systemPrompt,
                    userPrompt,
                    "answer grading");
            LlmGradingResponse parsedResponse = objectMapper.readValue(content, LlmGradingResponse.class);
            return new GradingResult(parsedResponse, content, deepseekModel);
        } catch (Exception e) {
            log.error("Failed to call LLM or parse grading response: {}", e.getMessage(), e);
            throw new LlmApiException("Failed to grade answer via LLM", e);
        }
    }

    public SimpleSentenceResult generateSimpleSentenceFromMaterial(String materialType, String materialContent) {
        String systemPrompt = """
                You create one simple English sentence for writing practice.
                Return JSON only:
                {
                  "sentence": "one simple English sentence"
                }
                Rules:
                1) Output exactly one complete English sentence.
                2) The sentence must naturally include the provided word or phrase.
                3) Keep it short and clear for beginner/intermediate learners.
                """;

        String userPrompt = String.format("Material type: %s%nMaterial content: %s",
                materialType == null ? "unknown" : materialType,
                materialContent);

        try {
            String content = callJsonChatCompletion(
                    deepseekModel,
                    systemPrompt,
                    userPrompt,
                    "simple sentence generation");
            String sentence = extractTextFromContent(
                    content,
                    " ",
                    "sentence",
                    "text",
                    "content",
                    "output");
            if (sentence.isBlank()) {
                throw new LlmApiException("Generated simple sentence is empty");
            }
            return new SimpleSentenceResult(sentence, content, deepseekModel);
        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate simple sentence: {}", e.getMessage(), e);
            throw new LlmApiException("Failed to generate simple sentence from material", e);
        }
    }

    public MaterialAutoParseResult analyzeAndSplitMaterialAuto(String rawContent) {
        String source = rawContent == null ? "" : rawContent.trim();
        if (source.isEmpty()) {
            return new MaterialAutoParseResult("unknown", List.of(), "{}", deepseekModel);
        }

        String systemPrompt = """
                You classify imported English learning material and split long sentence paragraphs.
                Return JSON only:
                {
                  "type": "word|phrase|sentence",
                  "segments": ["one item per material line"]
                }
                Rules:
                1) type must be exactly one of word, phrase, sentence.
                2) If input is one word, use type=word and return one segment.
                3) If input is a phrase (not a complete sentence), use type=phrase and return one segment.
                4) If input is a sentence or multi-sentence paragraph, use type=sentence.
                5) For type=sentence and multi-sentence input, split into multiple complete English sentences in segments.
                6) Keep original meaning and wording whenever possible, only trim spaces.
                7) Do not output empty segments.
                """;

        String userPrompt = "Input material:\n" + source;

        try {
            String content = callJsonChatCompletion(
                    deepseekModel,
                    systemPrompt,
                    userPrompt,
                    "material auto classification");

            MaterialAutoParseResult parsed = parseMaterialAutoParseResult(content, source);
            return new MaterialAutoParseResult(parsed.getType(), parsed.getSegments(), content, deepseekModel);
        } catch (Exception e) {
            log.warn("Auto material classification failed, fallback to local inference. reason={}", e.getMessage());
            String type = inferMaterialTypeLocally(source);
            return new MaterialAutoParseResult(type, fallbackSegmentsByType(source, type), "{}", "local_fallback");
        }
    }

    public QuestionGenerationResult generateQuestionFromWordOrPhrase(
            String materialType,
            String materialContent) {
        String systemPrompt = """
                You create one English sentence writing exercise from a word or phrase.
                Return JSON only:
                {
                  "chinesePrompt": "Simplified Chinese meaning of the target sentence",
                  "referenceSentence": "one complete English sentence",
                  "difficulty": 2
                }
                Rules:
                1) referenceSentence must be exactly one complete English sentence.
                2) The sentence must naturally include the provided material.
                3) chinesePrompt must match the meaning of referenceSentence and must be in Simplified Chinese only.
                4) Keep the sentence short and practical for beginner/intermediate learners.
                5) difficulty must be an integer from 1 to 5.
                """;

        String userPrompt = String.format(
                "Material type: %s%nMaterial content: %s",
                materialType == null ? "unknown" : materialType,
                materialContent);

        try {
            String content = callJsonChatCompletion(
                    deepseekModel,
                    systemPrompt,
                    userPrompt,
                    "word-or-phrase question generation");
            GeneratedQuestion parsedResponse = parseGeneratedQuestion(content);
            validateGeneratedQuestion(parsedResponse);
            return new QuestionGenerationResult(parsedResponse, content, deepseekModel);
        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate word-or-phrase question: {}", e.getMessage(), e);
            throw new LlmApiException("Failed to generate question from word or phrase via LLM", e);
        }
    }

    public TranslationResult translateEnglishToChinese(String englishSentence) {
        String systemPrompt = """
                You are a translation engine.
                Translate the English sentence to natural Simplified Chinese.
                Do not explain.
                Do not output the original English.
                Keep the original meaning and style.
                If the source sentence contains grammar mistakes, preserve that meaning and do not silently correct the intent.
                Return JSON only:
                {
                  "translation": "simplified Chinese translation"
                }
                """;

        String userPrompt = String.format("English sentence: %s", englishSentence);

        try {
            String content = callJsonChatCompletion(
                    translationModel,
                    systemPrompt,
                    userPrompt,
                    "english-to-chinese translation");
            String translation = normalizeExtractedText(extractTextFromContent(
                    content,
                    "",
                    "translation",
                    "translatedText",
                    "text",
                    "content",
                    "output"));

            if (!isUsableChineseTranslation(translation)) {
                String fallbackTranslation = fallbackTranslateByDeepSeek(englishSentence);
                return new TranslationResult(fallbackTranslation, content, translationModel + " -> " + deepseekModel);
            }
            return new TranslationResult(translation, content, translationModel);
        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to translate English to Chinese: {}", e.getMessage(), e);
            throw new LlmApiException("Failed to translate sentence via LLM", e);
        }
    }

    public QuestionGenerationResult generateErrorFocusedQuestion(
            String materialType,
            String materialContent,
            String primaryErrorType,
            String errorContext,
            String exerciseType) {
        String systemPrompt = """
                You generate one targeted English practice question based on error type and requested exercise type.
                Return JSON only:
                {
                  "prompt": "question text",
                  "referenceAnswer": "correct answer text",
                  "difficulty": 3
                }
                Rules:
                1) exerciseType can be: sentence_writing, fill_blank, correction.
                2) If exerciseType=sentence_writing, prompt asks the learner to write exactly one English sentence.
                3) If exerciseType=fill_blank, prompt includes one blank marked by ___.
                4) If exerciseType=correction, prompt gives an incorrect sentence and asks the learner to correct it.
                5) The question must specifically train the given error type (for example grammar, tense, spelling, collocation).
                6) referenceAnswer must match the prompt format and be concise.
                7) Difficulty must be an integer from 1 to 5.
                """;

        String userPrompt = String.format(
                "Material type: %s%nMaterial content: %s%nPrimary error type: %s%nError context: %s%nExercise type: %s",
                materialType == null ? "unknown" : materialType,
                materialContent,
                primaryErrorType == null ? "general" : primaryErrorType,
                errorContext == null ? "none" : errorContext,
                exerciseType == null ? "sentence_writing" : exerciseType);

        try {
            String content = callJsonChatCompletion(
                    deepseekModel,
                    systemPrompt,
                    userPrompt,
                    "error-focused exercise generation");
            GeneratedQuestion parsedResponse = parseGeneratedQuestion(content);
            validateGeneratedQuestion(parsedResponse);
            return new QuestionGenerationResult(parsedResponse, content, deepseekModel);
        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate error-focused exercise: {}", e.getMessage(), e);
            throw new LlmApiException("Failed to generate error-focused exercise via LLM", e);
        }
    }

    private String callJsonChatCompletion(
            String model,
            String systemPrompt,
            String userPrompt,
            String taskName) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmApiException("LLM_API_KEY is not configured");
        }

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", temperature,
                "response_format", Map.of("type", "json_object"));

        log.info("Calling LLM model {} for {}", model, taskName);
        long startTime = System.currentTimeMillis();

        try {
            Map responseMap = webClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(2)))
                    .block();

            long timeTaken = System.currentTimeMillis() - startTime;
            log.info("LLM responded in {} ms for {}", timeTaken, taskName);

            if (responseMap == null || !responseMap.containsKey("choices")) {
                throw new LlmApiException("Invalid response format from LLM provider");
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices.isEmpty() || choices.getFirst().get("message") == null) {
                throw new LlmApiException("No message found in LLM response");
            }

            Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
            String content = (String) message.get("content");
            if (content == null || content.isBlank()) {
                throw new LlmApiException("Empty content in LLM response");
            }

            return cleanJsonContent(content);
        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmApiException("Failed to call LLM provider", e);
        }
    }

    private void validateGeneratedQuestion(GeneratedQuestion question) {
        if (question == null) {
            throw new LlmApiException("Question generation response is null");
        }

        if (question.getPrompt() == null || question.getPrompt().isBlank()) {
            throw new LlmApiException("Question generation response has empty prompt");
        }

        if (question.getReferenceSentence() == null || question.getReferenceSentence().isBlank()) {
            throw new LlmApiException("Question generation response has empty referenceSentence");
        }

        if (question.getDifficulty() == null || question.getDifficulty() < 1 || question.getDifficulty() > 5) {
            throw new LlmApiException("Question generation response has invalid difficulty");
        }
    }

    private MaterialAutoParseResult parseMaterialAutoParseResult(String content, String fallbackSource) throws Exception {
        JsonNode root = objectMapper.readTree(content);
        JsonNode objectNode = resolvePrimaryObjectNode(root);

        String type = normalizeMaterialType(extractTextFromNode(
                objectNode,
                " ",
                "type",
                "materialType",
                "kind",
                "category"));

        List<String> segments = extractTextArrayFromNode(
                objectNode,
                "segments",
                "sentences",
                "items",
                "materials",
                "lines");
        segments = normalizeSegments(segments);

        if (type.isBlank()) {
            type = inferMaterialTypeLocally(fallbackSource);
        }
        if (segments.isEmpty()) {
            segments = fallbackSegmentsByType(fallbackSource, type);
        }

        if ("sentence".equals(type) && segments.size() == 1 && looksLikeMultiSentence(segments.getFirst())) {
            List<String> localSplit = splitIntoSentencesLocally(segments.getFirst());
            if (localSplit.size() > 1) {
                segments = localSplit;
            }
        }

        if (!"sentence".equals(type) && !segments.isEmpty()) {
            return new MaterialAutoParseResult(type, List.of(segments.getFirst()), content, deepseekModel);
        }

        return new MaterialAutoParseResult(type, segments, content, deepseekModel);
    }

    private String cleanJsonContent(String content) {
        if (content.startsWith("```json")) {
            return content.replace("```json", "").replace("```", "").trim();
        }
        if (content.startsWith("```")) {
            return content.replace("```", "").trim();
        }
        return content.trim();
    }

    private String fallbackTranslateByDeepSeek(String englishSentence) {
        String fallbackSystemPrompt = """
                Translate the input English sentence into Simplified Chinese.
                Output only Simplified Chinese text.
                Keep meaning aligned with the original sentence.
                Do not output English.
                """;
        String fallbackUserPrompt = String.format("English sentence: %s", englishSentence);

        String fallbackContent = callJsonChatCompletion(
                deepseekModel,
                fallbackSystemPrompt,
                fallbackUserPrompt,
                "fallback english-to-chinese translation");
        String translated = normalizeExtractedText(extractTextFromContent(
                fallbackContent,
                "",
                "translation",
                "translatedText",
                "text",
                "content",
                "output"));
        if (!isUsableChineseTranslation(translated)) {
            throw new LlmApiException("Fallback translation did not return usable Chinese text");
        }
        return translated;
    }

    private GeneratedQuestion parseGeneratedQuestion(String content) throws Exception {
        try {
            return objectMapper.readValue(content, GeneratedQuestion.class);
        } catch (Exception ignored) {
        }

        JsonNode root = objectMapper.readTree(content);
        JsonNode objectNode = resolvePrimaryObjectNode(root);

        GeneratedQuestion result = new GeneratedQuestion();
        String prompt = extractTextFromNode(
                objectNode,
                " ",
                "chinesePrompt",
                "promptZh",
                "prompt_zh",
                "translation",
                "prompt",
                "question",
                "task",
                "instruction",
                "content");
        String referenceSentence = extractTextFromNode(
                objectNode,
                " ",
                "referenceAnswer",
                "referenceSentence",
                "answer",
                "reference",
                "modelAnswer",
                "sentence",
                "text");
        Integer difficulty = extractDifficulty(objectNode);

        result.setPrompt(prompt);
        result.setReferenceSentence(referenceSentence);
        result.setDifficulty(difficulty == null ? 3 : difficulty);
        return result;
    }

    private Integer extractDifficulty(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode difficultyNode = node.get("difficulty");
        if (difficultyNode != null && difficultyNode.isNumber()) {
            return difficultyNode.asInt();
        }
        JsonNode levelNode = node.get("level");
        if (levelNode != null && levelNode.isNumber()) {
            return levelNode.asInt();
        }
        return null;
    }

    private String extractTextFromContent(String content, String delimiter, String... preferredFields) {
        if (content == null || content.isBlank()) {
            return "";
        }

        try {
            JsonNode root = objectMapper.readTree(content);
            String extracted = extractTextFromNode(root, delimiter, preferredFields);
            if (extracted != null && !extracted.isBlank()) {
                return extracted.trim();
            }
        } catch (Exception ignored) {
        }

        return stripWrappingQuotes(content.trim());
    }

    private String extractTextFromNode(JsonNode node, String delimiter, String... preferredFields) {
        if (node == null || node.isNull()) {
            return "";
        }

        if (node.isTextual()) {
            return node.asText().trim();
        }

        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }

        if (node.isObject()) {
            for (String field : preferredFields) {
                JsonNode preferred = node.get(field);
                if (preferred != null && !preferred.isNull()) {
                    String text = extractTextFromNode(preferred, delimiter, preferredFields);
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }

            List<String> parts = new ArrayList<>();
            node.fields().forEachRemaining(entry -> {
                String value = extractTextFromNode(entry.getValue(), delimiter, preferredFields);
                if (!value.isBlank()) {
                    parts.add(value.trim());
                }
            });
            return joinParts(parts, delimiter);
        }

        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode child : node) {
                String value = extractTextFromNode(child, delimiter, preferredFields);
                if (!value.isBlank()) {
                    parts.add(value.trim());
                }
            }
            return joinParts(parts, delimiter);
        }

        return "";
    }

    private JsonNode resolvePrimaryObjectNode(JsonNode root) {
        if (root == null || root.isNull()) {
            return root;
        }
        if (root.isObject()) {
            return root;
        }
        if (root.isArray() && !root.isEmpty()) {
            JsonNode first = root.get(0);
            if (first != null && first.isObject()) {
                return first;
            }
        }
        return root;
    }

    private String stripWrappingQuotes(String text) {
        if (text == null || text.length() < 2) {
            return text == null ? "" : text;
        }
        boolean wrappedByQuotes = (text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'"));
        if (!wrappedByQuotes) {
            return text;
        }
        return text.substring(1, text.length() - 1).trim();
    }

    private String normalizeExtractedText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", " ").replace("\n", " ").trim();
    }

    private boolean isUsableChineseTranslation(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim();
        if (normalized.isBlank()) {
            return false;
        }
        if ("[]".equals(normalized) || "[ ]".equals(normalized) || "{}".equals(normalized)) {
            return false;
        }
        // Require at least one CJK character to avoid English paraphrase leakage.
        return normalized.codePoints().anyMatch(cp -> cp >= 0x4E00 && cp <= 0x9FFF);
    }

    private String joinParts(List<String> parts, String delimiter) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return String.join(delimiter == null ? " " : delimiter, parts);
    }

    private List<String> extractTextArrayFromNode(JsonNode node, String... preferredFields) {
        if (node == null || node.isNull()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        if (node.isObject()) {
            for (String field : preferredFields) {
                JsonNode fieldNode = node.get(field);
                if (fieldNode == null || fieldNode.isNull()) {
                    continue;
                }
                if (fieldNode.isArray()) {
                    for (JsonNode child : fieldNode) {
                        String text = extractTextFromNode(child, " ", preferredFields);
                        if (!text.isBlank()) {
                            values.add(text);
                        }
                    }
                    if (!values.isEmpty()) {
                        return values;
                    }
                } else if (fieldNode.isTextual()) {
                    String text = fieldNode.asText().trim();
                    if (!text.isBlank()) {
                        values.add(text);
                        return values;
                    }
                }
            }
        }

        String textFallback = extractTextFromNode(node, " ", preferredFields);
        if (!textFallback.isBlank()) {
            values.add(textFallback);
        }
        return values;
    }

    private List<String> normalizeSegments(List<String> rawSegments) {
        if (rawSegments == null || rawSegments.isEmpty()) {
            return List.of();
        }

        Set<String> dedup = new LinkedHashSet<>();
        for (String raw : rawSegments) {
            if (raw == null) {
                continue;
            }
            String text = raw.replace("\r", " ").replace("\n", " ").trim();
            if (text.isBlank()) {
                continue;
            }
            dedup.add(text);
        }
        return new ArrayList<>(dedup);
    }

    private List<String> fallbackSegmentsByType(String source, String type) {
        if (source == null || source.isBlank()) {
            return List.of();
        }

        if (!"sentence".equals(type)) {
            return List.of(source.trim());
        }

        List<String> parts = splitIntoSentencesLocally(source);
        if (!parts.isEmpty()) {
            return parts;
        }
        return List.of(source.trim());
    }

    private List<String> splitIntoSentencesLocally(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.trim().replaceAll("\\s+", " ");
        String[] split = normalized.split("(?<=[.!?])\\s+");
        List<String> result = new ArrayList<>();
        for (String item : split) {
            String sentence = item == null ? "" : item.trim();
            if (!sentence.isBlank()) {
                result.add(sentence);
            }
        }
        return normalizeSegments(result);
    }

    private boolean looksLikeMultiSentence(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.trim();
        int sentenceEndings = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch == '.' || ch == '!' || ch == '?') {
                sentenceEndings++;
            }
        }
        return sentenceEndings >= 2;
    }

    private String inferMaterialTypeLocally(String source) {
        if (source == null || source.isBlank()) {
            return "unknown";
        }

        String normalized = source.trim();
        long tokenCount = List.of(normalized.split("\\s+")).stream()
                .filter(token -> !token.isBlank())
                .count();
        boolean hasSentenceEnd = normalized.matches(".*[.!?]$");

        if (tokenCount <= 1) {
            return "word";
        }
        if (tokenCount <= 4 && !hasSentenceEnd) {
            return "phrase";
        }
        return "sentence";
    }

    private String normalizeMaterialType(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("word")) {
            return "word";
        }
        if (normalized.contains("phrase")) {
            return "phrase";
        }
        if (normalized.contains("sentence") || normalized.contains("paragraph")) {
            return "sentence";
        }
        if ("word".equals(normalized) || "phrase".equals(normalized) || "sentence".equals(normalized)) {
            return normalized;
        }
        return "";
    }

    public static class GradingResult {
        private final LlmGradingResponse response;
        private final String rawResponse;
        private final String model;

        public GradingResult(LlmGradingResponse response, String rawResponse, String model) {
            this.response = response;
            this.rawResponse = rawResponse;
            this.model = model;
        }

        public LlmGradingResponse getResponse() {
            return response;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public String getModel() {
            return model;
        }
    }

    public static class SimpleSentenceResult {
        private final String sentence;
        private final String rawResponse;
        private final String model;

        public SimpleSentenceResult(String sentence, String rawResponse, String model) {
            this.sentence = sentence;
            this.rawResponse = rawResponse;
            this.model = model;
        }

        public String getSentence() {
            return sentence;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public String getModel() {
            return model;
        }
    }

    public static class TranslationResult {
        private final String translation;
        private final String rawResponse;
        private final String model;

        public TranslationResult(String translation, String rawResponse, String model) {
            this.translation = translation;
            this.rawResponse = rawResponse;
            this.model = model;
        }

        public String getTranslation() {
            return translation;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public String getModel() {
            return model;
        }
    }

    public static class QuestionGenerationResult {
        private final GeneratedQuestion response;
        private final String rawResponse;
        private final String model;

        public QuestionGenerationResult(GeneratedQuestion response, String rawResponse, String model) {
            this.response = response;
            this.rawResponse = rawResponse;
            this.model = model;
        }

        public GeneratedQuestion getResponse() {
            return response;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public String getModel() {
            return model;
        }
    }

    public static class MaterialAutoParseResult {
        private final String type;
        private final List<String> segments;
        private final String rawResponse;
        private final String model;

        public MaterialAutoParseResult(String type, List<String> segments, String rawResponse, String model) {
            this.type = type;
            this.segments = segments == null ? List.of() : segments;
            this.rawResponse = rawResponse;
            this.model = model;
        }

        public String getType() {
            return type;
        }

        public List<String> getSegments() {
            return segments;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public String getModel() {
            return model;
        }
    }

    public static class GeneratedQuestion {
        private String prompt;
        private String referenceSentence;
        private Integer difficulty;

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public String getReferenceSentence() {
            return referenceSentence;
        }

        public void setReferenceSentence(String referenceSentence) {
            this.referenceSentence = referenceSentence;
        }

        public Integer getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(Integer difficulty) {
            this.difficulty = difficulty;
        }
    }

}
