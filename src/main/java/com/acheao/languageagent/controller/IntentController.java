package com.acheao.languageagent.controller;


import com.acheao.languageagent.agent.IntentClassifier;
import com.acheao.languageagent.agent.model.IntentResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/intent")
public class IntentController {

    private final IntentClassifier intentClassifier;

    @Autowired
    public IntentController(IntentClassifier intentClassifier) {
        this.intentClassifier = intentClassifier;
    }

    /**
     * POST /api/intent/classify
     * 请求体: { "prompt": "用户输入内容" }
     */
    @PostMapping("/classify")
    public IntentResult classifyIntent(@RequestBody PromptRequest request) {
        return intentClassifier.classify(request.getPrompt());
    }

    // 内部静态类，用于接收请求体
    public static class PromptRequest {
        private String prompt;

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }
    }
}
