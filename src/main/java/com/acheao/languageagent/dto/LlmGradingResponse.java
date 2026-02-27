package com.acheao.languageagent.dto;

import java.util.List;

public class LlmGradingResponse {
    private int score;
    private boolean isCorrect;
    private String correctedAnswer;
    private List<String> errorTypes;
    private String explanationZh;
    private List<String> suggestions;

    // Getters and Setters
    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public boolean isCorrect() {
        return isCorrect;
    }

    public void setCorrect(boolean correct) {
        isCorrect = correct;
    }

    public String getCorrectedAnswer() {
        return correctedAnswer;
    }

    public void setCorrectedAnswer(String correctedAnswer) {
        this.correctedAnswer = correctedAnswer;
    }

    public List<String> getErrorTypes() {
        return errorTypes;
    }

    public void setErrorTypes(List<String> errorTypes) {
        this.errorTypes = errorTypes;
    }

    public String getExplanationZh() {
        return explanationZh;
    }

    public void setExplanationZh(String explanationZh) {
        this.explanationZh = explanationZh;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }
}
