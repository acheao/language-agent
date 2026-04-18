package com.acheao.languageagent.dto.res;

import com.acheao.languageagent.domain.entity.User;

import java.util.UUID;

public class UserProfileRes {
    private UUID id;
    private String email;
    private String displayName;
    private Integer dailyGoalMinutes;
    private Double targetIeltsScore;
    private boolean hasLlmConfig;

    public static UserProfileRes from(User user, boolean hasLlmConfig) {
        UserProfileRes res = new UserProfileRes();
        res.setId(user.getId());
        res.setEmail(user.getEmail());
        res.setDisplayName(user.getDisplayName());
        res.setDailyGoalMinutes(user.getDailyGoalMinutes());
        res.setTargetIeltsScore(user.getTargetIeltsScore());
        res.setHasLlmConfig(hasLlmConfig);
        return res;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getDailyGoalMinutes() {
        return dailyGoalMinutes;
    }

    public void setDailyGoalMinutes(Integer dailyGoalMinutes) {
        this.dailyGoalMinutes = dailyGoalMinutes;
    }

    public Double getTargetIeltsScore() {
        return targetIeltsScore;
    }

    public void setTargetIeltsScore(Double targetIeltsScore) {
        this.targetIeltsScore = targetIeltsScore;
    }

    public boolean isHasLlmConfig() {
        return hasLlmConfig;
    }

    public void setHasLlmConfig(boolean hasLlmConfig) {
        this.hasLlmConfig = hasLlmConfig;
    }
}
