package com.omnishelf.omnishelf_engine.service;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnishelf.omnishelf_engine.dto.ParsedOrder;
import com.omnishelf.omnishelf_engine.dto.ParsedItem;
import com.omnishelf.omnishelf_engine.dto.ParsedVariant;

@Component
@Slf4j
public class GeminiResponseValidator {

    private final ObjectMapper objectMapper;

    public GeminiResponseValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedOrder validateAndParse(String rawJson) {
        // 1. Sanitize: strip any markdown Gemini may have added despite instructions
        String clean = rawJson
            .replaceAll("```json", "")
            .replaceAll("```", "")
            .trim();

        // 2. Parse to our strongly-typed DTO
        ParsedOrder order;
        try {
            order = objectMapper.readValue(clean, ParsedOrder.class);
        } catch (JsonProcessingException e) {
            log.warn("Gemini returned non-parseable JSON: {}", clean);
            throw new InvalidGeminiResponseException("Response was not valid JSON");
        }

        // 3. Check for error flag
        if (order.getError() != null) {
            log.info("Gemini flagged message as unparseable: {}", order.getReason());
            return order; // return with error field set — caller handles it
        }

        // 4. Business rule validations
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new InvalidGeminiResponseException("No items extracted from message");
        }

        for (ParsedItem item : order.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                item.setQuantity(1); // safe default
            }
            if (item.getQuantity() > 999) {
                throw new InvalidGeminiResponseException(
                    "Suspicious quantity detected: " + item.getQuantity());
            }
            // Reject any item where BOTH brand fields are null
            if (item.getBrand() == null && item.getRawBrand() == null) {
                throw new InvalidGeminiResponseException(
                    "Item has no brand information: " + item.getRawText());
            }
        }

        return order;
    }
}