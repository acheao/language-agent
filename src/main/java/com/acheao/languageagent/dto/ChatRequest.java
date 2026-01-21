package com.acheao.languageagent.dto;

import java.util.List;
import java.util.Map;

public record ChatRequest(
        String model,                    // 必填，模型名称
        List<ChatMessage> messages,      // 必填，对话消息列表
        Boolean stream,                  // 是否开启流式
        Integer max_tokens,              // 最大生成长度
        Double temperature,              // 生成温度
        Double top_p,                    // nucleus 采样
        List<String> stop,               // 停止标记
        Map<String, Object> stream_options // 可选，流式配置
//        Map<String, Object> metadata     // 可选，额外信息
) {}
