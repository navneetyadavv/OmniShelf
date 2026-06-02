package com.omnishelf.omnishelf_engine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiService {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.endpoint}")
    private String endpoint;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String callGemini(String prompt) {
        String url = endpoint + "/" + model + ":generateContent?key=" + apiKey;

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", prompt)
                ))
            ),
            "generationConfig", Map.of(
                "temperature", 0.1,       // low temperature = deterministic, less creative
                "maxOutputTokens", 1024
            )
        );

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                url,
                new HttpEntity<>(requestBody, jsonHeaders()),
                Map.class
            );

            return extractTextFromGeminiResponse(response.getBody());

        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new GeminiApiException("Failed to call Gemini API", e);
        }
    }

    private String extractTextFromGeminiResponse(Map<?, ?> responseBody) {
        try {
            var candidates = (List<?>) responseBody.get("candidates");
            var first = (Map<?, ?>) candidates.get(0);
            var content = (Map<?, ?>) first.get("content");
            var parts = (List<?>) content.get("parts");
            var part = (Map<?, ?>) parts.get(0);
            return (String) part.get("text");
        } catch (Exception e) {
            throw new GeminiApiException("Could not parse Gemini response structure", e);
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}