package com.acheao.languageagent.controller;

import com.acheao.languageagent.dto.AiChatRequest;
import com.acheao.languageagent.service.AiChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/ai-employee/chat")
public class AiEmployeeChatController {

    private final AiChatService chatService;

    public AiEmployeeChatController(AiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@RequestBody AiChatRequest request) {

        SseEmitter emitter = new SseEmitter(0L); // 不超时

        chatService.streamChat(request, emitter);
//        /api/ai-chat/stop
//        /api/ai-chat/history
        return emitter;
    }


}
