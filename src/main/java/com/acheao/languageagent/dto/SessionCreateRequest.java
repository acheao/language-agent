package com.acheao.languageagent.dto;

public class SessionCreateRequest {
    private int batchSize = 10;
    private String generatorMode = "new";

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getGeneratorMode() {
        return generatorMode;
    }

    public void setGeneratorMode(String generatorMode) {
        this.generatorMode = generatorMode;
    }
}
