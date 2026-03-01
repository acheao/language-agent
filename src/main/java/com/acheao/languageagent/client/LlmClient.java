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
import java.util.List;
import java.util.Map;

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

    public TranslationResult translateEnglishToChinese(String englishSentence) {
        String systemPrompt = """
                You are a translation engine.
                Translate the English sentence to natural Simplified Chinese.
                Return JSON only:
                {
                  "translation": "simplified Chinese translation"
                }
                Keep the original meaning accurate and complete.
                """;

        String userPrompt = String.format("English sentence: %s", englishSentence);

        try {
            String content = callJsonChatCompletion(
                    translationModel,
                    systemPrompt,
                    userPrompt,
                    "english-to-chinese translation");
            String translation = extractTextFromContent(
                    content,
                    "",
                    "translation",
                    "translatedText",
                    "text",
                    "content",
                    "output");
            if (translation.isBlank()) {
                throw new LlmApiException("Translated Chinese text is empty");
            }
            return new TranslationResult(translation, content, translationModel);
        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to translate English to Chinese: {}", e.getMessage(), e);
            throw new LlmApiException("Failed to translate sentence via LLM", e);
        }
    }

    public QuestionGenerationResult generateErrorFocusedSentenceWritingQuestion(
            String materialType,
            String materialContent,
            String primaryErrorType,
            String errorContext) {
        String systemPrompt = """
                You generate one English sentence writing question targeted at a learner's weak error type.
                Return JSON only:
                {
                  "prompt": "question text",
                  "referenceSentence": "one model English sentence",
                  "difficulty": 3
                }
                Rules:
                1) Prompt must ask the learner to write exactly one English sentence.
                2) The question should specifically train the given error type (for example grammar, tense, spelling, word choice).
                3) Keep the sentence practical and clear.
                4) Difficulty must be an integer from 1 to 5.
                """;

        String userPrompt = String.format(
                "Material type: %s%nMaterial content: %s%nPrimary error type: %s%nError context: %s",
                materialType == null ? "unknown" : materialType,
                materialContent,
                primaryErrorType == null ? "general" : primaryErrorType,
                errorContext == null ? "none" : errorContext);

        try {
            String content = callJsonChatCompletion(
                    deepseekModel,
                    systemPrompt,
                    userPrompt,
                    "error-focused question generation");
            GeneratedQuestion parsedResponse = parseGeneratedQuestion(content);
            validateGeneratedQuestion(parsedResponse);
            return new QuestionGenerationResult(parsedResponse, content, deepseekModel);
        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate error-focused question: {}", e.getMessage(), e);
            throw new LlmApiException("Failed to generate error-focused question via LLM", e);
        }
    }

    private String callJsonChatCompletion(
            String model,
            String systemPrompt,
            String userPrompt,
            String taskName) {
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

    private String cleanJsonContent(String content) {
        if (content.startsWith("```json")) {
            return content.replace("```json", "").replace("```", "").trim();
        }
        if (content.startsWith("```")) {
            return content.replace("```", "").trim();
        }
        return content.trim();
    }

    private GeneratedQuestion parseGeneratedQuestion(String content) throws Exception {
        try {
            return objectMapper.readValue(content, GeneratedQuestion.class);
        } catch (Exception ignored) {
        }

        JsonNode root = objectMapper.readTree(content);
        JsonNode objectNode = resolvePrimaryObjectNode(root);

        GeneratedQuestion result = new GeneratedQuestion();
        String prompt = extractTextFromNode(objectNode, " ", "prompt", "question", "task", "instruction", "content");
        String referenceSentence = extractTextFromNode(
                objectNode,
                " ",
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

    private String joinParts(List<String> parts, String delimiter) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return String.join(delimiter == null ? " " : delimiter, parts);
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
