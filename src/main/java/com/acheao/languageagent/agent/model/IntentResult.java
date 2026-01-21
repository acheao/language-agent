package com.acheao.languageagent.agent.model;

import java.util.List;

public class IntentResult {
    private IntentType type;
    private String reason;
    private List<String> tasks;
    private String response;

    public IntentType getType() {
        return type;
    }

    public void setType(IntentType type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getTasks() {
        return tasks;
    }

    public void setTasks(List<String> tasks) {
        this.tasks = tasks;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }


}

