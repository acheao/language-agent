package com.acheao.languageagent.v2.entity;

import com.acheao.languageagent.domain.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "practice_submissions_v2")
public class PracticeSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practice_task_id", nullable = false)
    private PracticeTask practiceTask;

    @Column(columnDefinition = "TEXT")
    private String answerText;

    @Column(nullable = false)
    private Integer durationSeconds = 0;

    @Column(nullable = false)
    private boolean usedHint = false;

    @Column(nullable = false)
    private boolean skipped = false;

    @Column(nullable = false)
    private boolean uncertain = false;

    @Column(nullable = false)
    private Integer score = 0;

    @Column(nullable = false)
    private boolean correct = false;

    @Column(length = 255)
    private String shortFeedback;

    @Column(columnDefinition = "TEXT")
    private String detailedFeedback;

    @Column(length = 255)
    private String suggestion;

    @Column(columnDefinition = "TEXT")
    private String errorTypesJson;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public PracticeTask getPracticeTask() {
        return practiceTask;
    }

    public void setPracticeTask(PracticeTask practiceTask) {
        this.practiceTask = practiceTask;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public boolean isUsedHint() {
        return usedHint;
    }

    public void setUsedHint(boolean usedHint) {
        this.usedHint = usedHint;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public boolean isUncertain() {
        return uncertain;
    }

    public void setUncertain(boolean uncertain) {
        this.uncertain = uncertain;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    public String getShortFeedback() {
        return shortFeedback;
    }

    public void setShortFeedback(String shortFeedback) {
        this.shortFeedback = shortFeedback;
    }

    public String getDetailedFeedback() {
        return detailedFeedback;
    }

    public void setDetailedFeedback(String detailedFeedback) {
        this.detailedFeedback = detailedFeedback;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getErrorTypesJson() {
        return errorTypesJson;
    }

    public void setErrorTypesJson(String errorTypesJson) {
        this.errorTypesJson = errorTypesJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
