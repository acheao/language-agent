package com.acheao.languageagent.v2.service;

import com.acheao.languageagent.exception.LlmApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GenericLlmGateway {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GenericLlmGateway(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public JsonNode chatJson(RuntimeConfig config, String systemPrompt, String userPrompt) {
        String response = chatContent(config, systemPrompt, userPrompt, true);
        try {
            return objectMapper.readTree(cleanJsonContent(response));
        } catch (Exception e) {
            throw new LlmApiException("Failed to parse LLM JSON response", e);
        }
    }

    public String chatText(RuntimeConfig config, String systemPrompt, String userPrompt) {
        return cleanJsonContent(chatContent(config, systemPrompt, userPrompt, false));
    }

    public void ping(RuntimeConfig config) {
        chatText(config, "You are a readiness probe.", "Reply with OK only.");
    }

    private String chatContent(RuntimeConfig config, String systemPrompt, String userPrompt, boolean jsonMode) {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            throw new LlmApiException("LLM api key is missing");
        }

        Map<String, Object> payload = Map.of(
                "model", config.model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "temperature", 0.2,
                "response_format", jsonMode ? Map.of("type", "json_object") : Map.of());

        try {
            Map<?, ?> response = webClient.post()
                    .uri(config.baseUrl() + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response == null || !response.containsKey("choices")) {
                throw new LlmApiException("LLM provider returned an invalid response");
            }

            List<?> choices = (List<?>) response.get("choices");
            if (choices.isEmpty()) {
                throw new LlmApiException("LLM provider returned no choices");
            }
            Map<?, ?> firstChoice = (Map<?, ?>) choices.getFirst();
            Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
            if (message == null || message.get("content") == null) {
                throw new LlmApiException("LLM provider returned empty content");
            }
            return String.valueOf(message.get("content"));
        } catch (LlmApiException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmApiException("Failed to call LLM provider", e);
        }
    }

    private String cleanJsonContent(String content) {
        if (content == null) {
            return "";
        }
        String cleaned = content.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim();
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }
        return cleaned;
    }

    public record RuntimeConfig(String provider, String model, String baseUrl, String apiKey) {
    }
}
