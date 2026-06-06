package com.omnishelf.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnishelf.engine.dto.ParsedOrder;
import com.omnishelf.engine.exception.GeminiApiException;
import com.omnishelf.engine.exception.InvalidGeminiResponseException;
import com.omnishelf.engine.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NlpParserService {

    private final GeminiService       geminiService;
    private final GeminiPromptBuilder promptBuilder;
    private final ProductRepository   productRepo;
    private final ObjectMapper        objectMapper = new ObjectMapper();

    /**
     * Parses a raw shopkeeper message into a structured ParsedOrder.
     * Falls back to null if Gemini is unreachable, allowing the caller
     * to trigger the rule-based fallback parser.
     */
    public ParsedOrder parse(String rawMessage) {
        List<String> brands     = productRepo.findAllDistinctBrands();
        List<String> categories = productRepo.findAllDistinctCategories();

        String prompt = promptBuilder.buildExtractionPrompt(rawMessage, brands, categories);
        log.debug("NLP parse request [prompt_version={}]: {}", GeminiPromptBuilder.PROMPT_VERSION, rawMessage);

        String raw;
        try {
            raw = geminiService.callGemini(prompt);
        } catch (GeminiApiException e) {
            log.warn("Gemini unavailable — returning null for fallback: {}", e.getMessage());
            return null;  // caller will use FallbackNlpParserService
        }

        // Strip any accidental markdown fences Gemini sometimes includes
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }

        try {
            ParsedOrder order = objectMapper.readValue(cleaned, ParsedOrder.class);

            // If Gemini returned an error object, propagate it cleanly
            if (order.getError() != null) {
                log.info("Gemini returned error '{}': {}", order.getError(), order.getReason());
                return order;
            }

            if (order.getItems() == null || order.getItems().isEmpty()) {
                throw new InvalidGeminiResponseException("Gemini returned empty items list");
            }

            log.info("NLP parsed {} item(s), confidence={}, lang={}",
                order.getItems().size(), order.getConfidence(), order.getLanguage());
            return order;

        } catch (Exception e) {
            log.error("Failed to deserialize Gemini response: {}\nRaw response: {}", e.getMessage(), cleaned);
            throw new InvalidGeminiResponseException("Could not parse Gemini JSON response", e);
        }
    }
}
