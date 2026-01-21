package com.acheao.languageagent.dto;

public record ChatMessage(
        String role,   // system / user / assistant
        String content
) {
}

