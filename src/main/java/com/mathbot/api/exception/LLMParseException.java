package com.mathbot.api.exception;

public class LLMParseException extends RuntimeException {

    public LLMParseException(String message) {
        super(message);
    }

    public LLMParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
