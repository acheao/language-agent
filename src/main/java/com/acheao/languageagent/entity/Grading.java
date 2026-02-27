package com.acheao.languageagent.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "gradings")
public class Grading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "answer_id", nullable = false)
    private UUID answerId;

    @Column(nullable = false)
    private int score;

    @Column(name = "is_correct", nullable = false)
    private boolean isCorrect;

    @Column(name = "corrected_answer", columnDefinition = "text")
    private String correctedAnswer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_types", columnDefinition = "jsonb")
    private String errorTypes;

    @Column(name = "explanation_zh", columnDefinition = "text")
    private String explanationZh;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String suggestions;

    @Column(name = "raw_llm_response", columnDefinition = "text")
    private String rawLlmResponse;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAnswerId() {
        return answerId;
    }

    public void setAnswerId(UUID answerId) {
        this.answerId = answerId;
    }

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

    public String getErrorTypes() {
        return errorTypes;
    }

    public void setErrorTypes(String errorTypes) {
        this.errorTypes = errorTypes;
    }

    public String getExplanationZh() {
        return explanationZh;
    }

    public void setExplanationZh(String explanationZh) {
        this.explanationZh = explanationZh;
    }

    public String getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(String suggestions) {
        this.suggestions = suggestions;
    }

    public String getRawLlmResponse() {
        return rawLlmResponse;
    }

    public void setRawLlmResponse(String rawLlmResponse) {
        this.rawLlmResponse = rawLlmResponse;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
