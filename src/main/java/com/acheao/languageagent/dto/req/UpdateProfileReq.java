package com.acheao.languageagent.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public class UpdateProfileReq {
    @Size(max = 80, message = "Display name must be shorter than 80 characters")
    private String displayName;

    @Min(value = 10, message = "Daily goal minutes must be between 10 and 180")
    @Max(value = 180, message = "Daily goal minutes must be between 10 and 180")
    private Integer dailyGoalMinutes;

    @Min(value = 0, message = "IELTS target score must be between 0 and 9")
    @Max(value = 9, message = "IELTS target score must be between 0 and 9")
    private Double targetIeltsScore;

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
}
