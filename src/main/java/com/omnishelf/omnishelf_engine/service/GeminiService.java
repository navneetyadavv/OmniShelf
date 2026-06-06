package com.omnishelf.engine.service;

import com.omnishelf.engine.exception.GeminiApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class GeminiService {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.endpoint}")
    private String endpoint;

    @Value("${gemini.max-tokens:1024}")
    private int maxTokens;

    @Value("${gemini.temperature:0.1}")
    private double temperature;

    private final RestTemplate restTemplate = new RestTemplate();

    // Daily token usage counter for cost monitoring
    private final AtomicLong dailyTokensUsed = new AtomicLong(0);
    private volatile long tokenResetDay = System.currentTimeMillis() / 86_400_000;

    public String callGemini(String prompt) {
        resetTokenCounterIfNewDay();

        String url = endpoint + "/" + model + ":generateContent?key=" + apiKey;

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
            "generationConfig", Map.of(
                "temperature",     temperature,
                "maxOutputTokens", maxTokens
            )
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                url, new HttpEntity<>(requestBody, headers), Map.class);

            return extractText(response.getBody());

        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new GeminiApiException("Failed to call Gemini API", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<?, ?> body) {
        try {
            // Log token usage if available
            if (body.containsKey("usageMetadata")) {
                Map<?, ?> usage = (Map<?, ?>) body.get("usageMetadata");
                Object total = usage.get("totalTokenCount");
                if (total instanceof Number n) {
                    long used = dailyTokensUsed.addAndGet(n.longValue());
                    log.debug("Gemini tokens — request: {}, daily total: {}", n, used);
                    if (used > 50_000) {
                        log.warn("High Gemini token usage today: {} tokens", used);
                    }
                }
            }

            var candidates = (List<?>) body.get("candidates");
            var first      = (Map<?, ?>) candidates.get(0);
            var content    = (Map<?, ?>) first.get("content");
            var parts      = (List<?>) content.get("parts");
            var part       = (Map<?, ?>) parts.get(0);
            return (String) part.get("text");

        } catch (Exception e) {
            throw new GeminiApiException("Could not parse Gemini response structure", e);
        }
    }

    private void resetTokenCounterIfNewDay() {
        long today = System.currentTimeMillis() / 86_400_000;
        if (today > tokenResetDay) {
            tokenResetDay = today;
            dailyTokensUsed.set(0);
            log.info("Gemini daily token counter reset");
        }
    }
}
