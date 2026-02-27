package com.acheao.languageagent.dto;

import java.util.List;

public class MaterialImportRequest {
    private List<String> lines;
    private String type;

    public List<String> getLines() {
        return lines;
    }

    public void setLines(List<String> lines) {
        this.lines = lines;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
