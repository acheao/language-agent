package com.acheao.languageagent.service;

import com.acheao.languageagent.dto.ChatMessage;

import java.util.List;
import java.util.function.Consumer;

public interface LlmClient {

    void streamChat(

            List<ChatMessage> messages,

            Consumer<String> onToken
    ) throws InterruptedException;

    String complete(String prompt);

}

