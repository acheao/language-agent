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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "study_units")
public class StudyUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(nullable = false)
    private Integer orderIndex;

    @Column(nullable = false, length = 24)
    private String kind = "sentence";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalText;

    @Column(columnDefinition = "TEXT")
    private String translationZh;

    @Column(columnDefinition = "TEXT")
    private String contextText;

    @Column
    private Double startSeconds;

    @Column
    private Double endSeconds;

    @Column(columnDefinition = "TEXT")
    private String tagsJson;

    @Column(nullable = false)
    private boolean inPracticePool = true;

    @Column(nullable = false)
    private boolean favorite = false;

    @Column(nullable = false)
    private boolean ignored = false;

    @Column(nullable = false)
    private Integer masteryScore = 0;

    @Column(nullable = false)
    private Double averageScore = 0.0;

    @Column(nullable = false)
    private Double averageDurationSeconds = 0.0;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(nullable = false)
    private Integer skipCount = 0;

    @Column(nullable = false)
    private Integer uncertainCount = 0;

    @Column
    private LocalDateTime nextReviewAt;

    @Column
    private LocalDateTime lastPracticedAt;

    @Column(length = 40)
    private String lastErrorType;

    @Column(nullable = false)
    private Integer difficulty = 2;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

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

    public Lesson getLesson() {
        return lesson;
    }

    public void setLesson(Lesson lesson) {
        this.lesson = lesson;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getTranslationZh() {
        return translationZh;
    }

    public void setTranslationZh(String translationZh) {
        this.translationZh = translationZh;
    }

    public String getContextText() {
        return contextText;
    }

    public void setContextText(String contextText) {
        this.contextText = contextText;
    }

    public Double getStartSeconds() {
        return startSeconds;
    }

    public void setStartSeconds(Double startSeconds) {
        this.startSeconds = startSeconds;
    }

    public Double getEndSeconds() {
        return endSeconds;
    }

    public void setEndSeconds(Double endSeconds) {
        this.endSeconds = endSeconds;
    }

    public String getTagsJson() {
        return tagsJson;
    }

    public void setTagsJson(String tagsJson) {
        this.tagsJson = tagsJson;
    }

    public boolean isInPracticePool() {
        return inPracticePool;
    }

    public void setInPracticePool(boolean inPracticePool) {
        this.inPracticePool = inPracticePool;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }

    public Integer getMasteryScore() {
        return masteryScore;
    }

    public void setMasteryScore(Integer masteryScore) {
        this.masteryScore = masteryScore;
    }

    public Double getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(Double averageScore) {
        this.averageScore = averageScore;
    }

    public Double getAverageDurationSeconds() {
        return averageDurationSeconds;
    }

    public void setAverageDurationSeconds(Double averageDurationSeconds) {
        this.averageDurationSeconds = averageDurationSeconds;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public Integer getSkipCount() {
        return skipCount;
    }

    public void setSkipCount(Integer skipCount) {
        this.skipCount = skipCount;
    }

    public Integer getUncertainCount() {
        return uncertainCount;
    }

    public void setUncertainCount(Integer uncertainCount) {
        this.uncertainCount = uncertainCount;
    }

    public LocalDateTime getNextReviewAt() {
        return nextReviewAt;
    }

    public void setNextReviewAt(LocalDateTime nextReviewAt) {
        this.nextReviewAt = nextReviewAt;
    }

    public LocalDateTime getLastPracticedAt() {
        return lastPracticedAt;
    }

    public void setLastPracticedAt(LocalDateTime lastPracticedAt) {
        this.lastPracticedAt = lastPracticedAt;
    }

    public String getLastErrorType() {
        return lastErrorType;
    }

    public void setLastErrorType(String lastErrorType) {
        this.lastErrorType = lastErrorType;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
