package com.acheao.languageagent.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "material_stats")
public class MaterialStats {

    @Id
    @Column(name = "material_id")
    private UUID materialId;

    @Column(name = "practice_count", nullable = false)
    private int practiceCount = 0;

    @Column(name = "correct_count", nullable = false)
    private int correctCount = 0;

    @Column(name = "last_practiced_at")
    private LocalDateTime lastPracticedAt;

    @Column(name = "next_review_at")
    private LocalDateTime nextReviewAt;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays = 0;

    @Column(nullable = false)
    private double ease = 2.5;

    @Column(name = "cooldown_until")
    private LocalDateTime cooldownUntil;

    // Getters and Setters
    public UUID getMaterialId() {
        return materialId;
    }

    public void setMaterialId(UUID materialId) {
        this.materialId = materialId;
    }

    public int getPracticeCount() {
        return practiceCount;
    }

    public void setPracticeCount(int practiceCount) {
        this.practiceCount = practiceCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public LocalDateTime getLastPracticedAt() {
        return lastPracticedAt;
    }

    public void setLastPracticedAt(LocalDateTime lastPracticedAt) {
        this.lastPracticedAt = lastPracticedAt;
    }

    public LocalDateTime getNextReviewAt() {
        return nextReviewAt;
    }

    public void setNextReviewAt(LocalDateTime nextReviewAt) {
        this.nextReviewAt = nextReviewAt;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    public double getEase() {
        return ease;
    }

    public void setEase(double ease) {
        this.ease = ease;
    }

    public LocalDateTime getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(LocalDateTime cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }
}
