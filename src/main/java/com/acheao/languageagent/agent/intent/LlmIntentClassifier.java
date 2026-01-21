package com.acheao.languageagent.agent.intent;

import com.acheao.languageagent.agent.IntentClassifier;
import com.acheao.languageagent.agent.model.IntentResult;
import com.acheao.languageagent.agent.model.IntentType;
import com.acheao.languageagent.service.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LlmIntentClassifier implements IntentClassifier {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(LlmIntentClassifier.class);

    public LlmIntentClassifier(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @Override
    public IntentResult classify(String userInput) {
        String prompt = buildPrompt(userInput);

        String llmOutput = llmClient.complete(prompt);
        logger.info("llm response: {}", llmOutput);
        return parseResult(llmOutput);
    }

    private String buildPrompt(String userInput) {
        return """
        You are an AI Agent’s [Intent Analyzer].
                        
        Please determine which of the following categories the user input belongs to:
        1. chat: general conversation, no specific actions need to be performed
        2. task: task instructions that can be broken down into execution steps; break down tasks as needed
        The output must be in JSON format only, without any extra text.
        Output format:
        {
          "type": "chat | task",
          "reason": "basis for your judgment",
          "tasks": [],
          "response": "response to the user, e.g., 'Understood, executing the following tasks: task1: tasks[0], ...'"
        }
        Rules:
        - If chat, tasks should be an empty array
        - If task, it must be broken down into clear tasks
        - If the input contains both conversation and instructions, treat it as task
        - Response content should be consistent with the user’s input language 
        User input:
        """ + userInput;
    }

    private IntentResult parseResult(String llmApiResponse) {
        try {
            // 1. 解析 LLM 原始响应
            JsonNode root = objectMapper.readTree(llmApiResponse);

            // 2. 提取 choices[0].message.content
            JsonNode contentNode = root
                    .path("data")         // 注意这里要先取 data
                    .path("choices")
                    .get(0)
                    .path("message")
                    .path("content");

            String content = contentNode.asText();

            // 3. 再把 content 转成 IntentResult
            return objectMapper.readValue(content, IntentResult.class);

        } catch (Exception e) {
            e.printStackTrace();
            // 兜底策略
            IntentResult fallback = new IntentResult();
            fallback.setType(IntentType.CHAT);
            fallback.setReason("LLM 输出解析失败，使用兜底策略");
            fallback.setTasks(List.of());
            fallback.setResponse("您好，服务暂时无法使用");
            return fallback;
        }
    }


    public boolean looksLikeTask(String input) {
        return input.matches(".*(生成|创建|实现|调用|查询|删除|修改|编写).*");
    }

}

