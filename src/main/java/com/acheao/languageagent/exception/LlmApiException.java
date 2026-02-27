package com.acheao.languageagent.exception;

public class LlmApiException extends RuntimeException {
    public LlmApiException(String message) {
        super(message);
    }
    public LlmApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
