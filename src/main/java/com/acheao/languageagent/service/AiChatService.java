package com.acheao.languageagent.service;

import com.acheao.languageagent.dto.AiChatRequest;
import com.acheao.languageagent.dto.ChatMessage;
import com.acheao.languageagent.entity.AiEmployee;
import com.acheao.languageagent.repository.AiEmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class AiChatService {

    private final LlmClient llmClient;
    private final AiEmployeeRepository employeeRepository;

    public AiChatService(LlmClient llmClient,
                         AiEmployeeRepository employeeRepository) {
        this.llmClient = llmClient;
        this.employeeRepository = employeeRepository;
    }

    public void streamChat(AiChatRequest req, SseEmitter emitter) {

        AiEmployee employee = employeeRepository.findById(req.employeeId())
                .orElseThrow(() -> new IllegalArgumentException("AI 员工不存在"));

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", employee.getBasePrompt()));

        if (employee.getPersonalizationPrompt() != null) {
            messages.add(new ChatMessage("system", employee.getPersonalizationPrompt()));
        }

        messages.add(new ChatMessage("user", req.message()));

        // 异步流式调用 LLM
        CompletableFuture.runAsync(() -> {
            try {
                llmClient.streamChat(messages, token -> {
                    try {
                        if ("[STREAM_COMPLETED]".equals(token)) {
                            emitter.complete(); // 只有在流结束时完成 SSE
                        } else {
                            emitter.send(SseEmitter.event().data(token));
                        }
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                });
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
    }

}


