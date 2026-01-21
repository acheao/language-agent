package com.acheao.languageagent.service.impl;

import com.acheao.languageagent.dto.ChatMessage;
import com.acheao.languageagent.dto.ChatRequest;
import com.acheao.languageagent.service.LlmClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class BaseLlmClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(BaseLlmClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BaseLlmClient() {
        this.webClient = WebClient.builder()
                .baseUrl("http://159.75.118.61:6092")
                .defaultHeader("X-api-key", "AK-diZN8KiIH01E7IygvWneg63KWIpCcqegyRvx")
                .defaultHeader("X-Session-Id", "1112")
                .build();
    }

    @Override
    public void streamChat(List<ChatMessage> messages, Consumer<String> onToken) {
        ChatRequest request = new ChatRequest(
                "gpt-4o",
                messages,
                true,             // stream
                1000,            // max_tokens
                0.7,              // temperature
                0.0,              // top_p
                List.of(),        // stop
                Map.of()         // stream_options
        );

        Flux<String> flux = webClient.post()
                .uri("/v1/chat/completions")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class);

        flux.subscribe(
                line -> {
                    try {
                        line = line.trim();
                        if (line.isEmpty()) return;

                        // 处理 SSE 前缀
                        if (line.startsWith("data: ")) line = line.substring(6);

                        // 忽略 DONE 标记
                        if (line.equals("[DONE]") || line.equals("DONE")) {
                            logger.info("\n回答完成");
                            return;
                        }

                        Map<String, Object> chunk = null;
                        if (line.startsWith("[")) {
                            // JSON 数组
                            List<Map<String, Object>> list = objectMapper.readValue(line, new TypeReference<>() {
                            });
                            if (!list.isEmpty()) chunk = list.get(0);
                        } else if (line.startsWith("{")) {
                            // JSON 对象
                            chunk = objectMapper.readValue(line, new TypeReference<>() {
                            });
                        } else {
                            // 非 JSON 内容，直接忽略
                            return;
                        }

                        if (chunk != null) {
                            List<Map<String, Object>> choices = (List<Map<String, Object>>) chunk.get("choices");
                            if (choices != null && !choices.isEmpty()) {
                                Map choice = choices.get(0);
                                Map delta = (Map) choice.get("delta");
                                if (delta != null && delta.get("content") != null) {
                                    String token = delta.get("content").toString();
                                    logger.info(token);
                                    onToken.accept(token);
                                }
                                if (choice.get("finish_reason") != null) {
                                    logger.info("the reason of finis: {}", choice.get("finish_reason"));
                                }
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        onToken.accept("[ERROR] " + e.getMessage());
                    }
                },
                err -> {
                    err.printStackTrace();
                    onToken.accept("[ERROR] " + err.getMessage());
                },
                () -> onToken.accept("[STREAM_COMPLETED]")
        );

    }

    @Override
    public String complete(String prompt) {
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage message = new ChatMessage("system", prompt);
        messages.add(message);
        ChatRequest request = new ChatRequest(
                "gpt-4o",
                messages,
                true,             // stream
                1000,            // max_tokens
                0.7,              // temperature
                0.0,              // top_p
                List.of(),        // stop
                Map.of()         // stream_options
        );
        String response = webClient.post()
                .uri("/v1/chat/completions")
                .accept(MediaType.APPLICATION_JSON)   // 普通 JSON
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)            // 非流式
                .block();
        return response;
    }
}
