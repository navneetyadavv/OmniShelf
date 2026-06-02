package com.omnishelf.omnishelf_engine.service;

public class InvalidGeminiResponseException extends RuntimeException {
    public InvalidGeminiResponseException() { super(); }
    public InvalidGeminiResponseException(String message) { super(message); }
    public InvalidGeminiResponseException(String message, Throwable cause) { super(message, cause); }
}
