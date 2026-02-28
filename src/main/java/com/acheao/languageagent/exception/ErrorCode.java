package com.acheao.languageagent.exception;

public enum ErrorCode {
    SUCCESS(0, "Success"),
    VALIDATION_ERROR(2001, "Validation Error"),
    NOT_FOUND(4004, "Resource Not Found"),
    INTERNAL_ERROR(5000, "Internal Server Error");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
