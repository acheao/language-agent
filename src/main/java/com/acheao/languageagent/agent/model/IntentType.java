package com.acheao.languageagent.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum IntentType {
    @JsonProperty("chat")
    CHAT,

    @JsonProperty("task")
    TASK
}
