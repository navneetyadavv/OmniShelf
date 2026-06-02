package com.omnishelf.omnishelf_engine.service;

public class GeminiApiException extends RuntimeException {
    public GeminiApiException() { super(); }
    public GeminiApiException(String message) { super(message); }
    public GeminiApiException(String message, Throwable cause) { super(message, cause); }
}
