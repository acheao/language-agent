package com.acheao.languageagent.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "对话实体")
public record AiChatRequest(

        @Schema(description = "租户ID")
        Long tenantId,

        @Schema(description = "对话的AI员工ID")
        Long employeeId,

        @Schema(description = "对话内容")
        String message        // 用户输入

//        List<ChatMessage> history // 可选：上下文
) {
}
