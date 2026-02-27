package com.acheao.languageagent.client;

import com.acheao.languageagent.dto.LlmGradingResponse;
import com.acheao.languageagent.exception.LlmApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
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

    @Value("${llm.model}")
    private String model;

    @Value("${llm.timeout-ms:20000}")
    private long timeoutMs;

    @Value("${llm.temperature:0.2}")
    private double temperature;

    public LlmClient(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public GradingResult gradeAnswer(String prompt, String referenceAnswer, String rubric, String userAnswer) {
        String systemPrompt = "你是一个英语批改助手。请根据提供的题目、参考答案、评分标准以及用户的回答，进行严格批改。" +
                "你必须只返回一个JSON格式的数据，不要包含任何markdown格式（如 ```json 等），确保其可以直接被解析。" +
                "JSON结构如下：\n" +
                "{\n" +
                "  \"score\": 0（0-100的整数）,\n" +
                "  \"isCorrect\": true/false,\n" +
                "  \"correctedAnswer\": \"修改后的正确句子（如果全对可以和原句一样）\",\n" +
                "  \"errorTypes\": [\"语法错误\", \"拼写错误\"...（如果没有错误则为空数组）],\n" +
                "  \"explanationZh\": \"中文解析说明\",\n" +
                "  \"suggestions\": [\"改进建议1\", \"改进建议2\"]\n" +
                "}";

        String userPrompt = String.format("题目: %s\n参考答案: %s\n评分标准: %s\n用户回答: %s",
                prompt, referenceAnswer, rubric, userAnswer);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", temperature,
                "response_format", Map.of("type", "json_object"));

        log.info("Calling LLM Model: {} for answer grading", model);
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
            log.info("LLM responded in {} ms", timeTaken);

            if (responseMap == null || !responseMap.containsKey("choices")) {
                throw new LlmApiException("Invalid response format from LLM provider");
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            // Clean markdown jsons
            if (content.startsWith("```json")) {
                content = content.replace("```json", "").replace("```", "").trim();
            }

            LlmGradingResponse parsedResponse = objectMapper.readValue(content, LlmGradingResponse.class);
            return new GradingResult(parsedResponse, content);

        } catch (Exception e) {
            log.error("Failed to call LLM or parse response: {}", e.getMessage(), e);
            throw new LlmApiException("Failed to grade answer via LLM", e);
        }
    }

    public static class GradingResult {
        private final LlmGradingResponse response;
        private final String rawResponse;

        public GradingResult(LlmGradingResponse response, String rawResponse) {
            this.response = response;
            this.rawResponse = rawResponse;
        }

        public LlmGradingResponse getResponse() {
            return response;
        }

        public String getRawResponse() {
            return rawResponse;
        }
    }
}
