package com.omnishelf.omnishelf_engine.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import com.omnishelf.omnishelf_engine.dto.ParsedOrder;
import com.omnishelf.omnishelf_engine.dto.ParsedItem;
import com.omnishelf.omnishelf_engine.dto.MatchResult;
import com.omnishelf.omnishelf_engine.repository.ProductRepository;

@Service
@Slf4j
@RequiredArgsConstructor // Lombok automatically builds the constructor with all final fields
public class NlpOrchestrationService {

    private final GeminiService geminiService;
    private final GeminiPromptBuilder promptBuilder;
    private final GeminiResponseValidator validator;
    private final EntityMatcherService entityMatcher;
    private final ProductRepository productRepository;
    private final TwilioMessagingService twilioMessaging;
    private final SessionManagerService sessionManager; // Added dependency

    public void processOrderMessage(String phone, String rawMessage) {
        try {
            // 1. Build context-aware prompt
            List<String> brands     = productRepository.findAllDistinctBrands();
            List<String> categories = productRepository.findAllDistinctCategories();
            String prompt = promptBuilder.buildExtractionPrompt(rawMessage, brands, categories);

            // 2. Call Gemini
            String rawJson = geminiService.callGemini(prompt);
            log.debug("Gemini raw response: {}", rawJson);

            // 3. Validate schema (injection firewall)
            ParsedOrder parsedOrder = validator.validateAndParse(rawJson);

            // 4. Handle unparseable
            if (parsedOrder.getError() != null) {
                twilioMessaging.send(phone,
                    "Samajh nahi aaya! Please try again.\n" +
                    "Example: \"2 Nike size 8\" or \"1 Samsung S24 blue\"");
                return;
            }

            // 5. Match each item against DB
            List<String> confirmationLines = new ArrayList<>();
            List<String> disambiguationLines = new ArrayList<>();
            boolean allMatched = true;

            for (ParsedItem item : parsedOrder.getItems()) {
                MatchResult result = entityMatcher.matchItem(item);

                switch (result.getStatus()) {
                    case FOUND -> {
                        // Fixed syntax: Separated the string creation and the session manager call
                        confirmationLines.add(
                            String.format("✓ %dx %s — ₹%.0f each",
                                result.getRequestedQuantity(),
                                result.getVariant().getSku(),
                                result.getVariant().getPrice())
                        );
                        
                        sessionManager.addItemToSession(
                            phone,
                            result.getVariant(),
                            item.getQuantity(),
                            parsedOrder.getCustomerName()
                        );
                    }
                    case FUZZY_MATCH -> disambiguationLines.add(
                        String.format("Did you mean *%s* for \"%s\"? Reply YES or NO.",
                            result.getSuggestedBrand(),
                            result.getOriginalInput())
                    );
                    case INSUFFICIENT_STOCK -> {
                        allMatched = false;
                        twilioMessaging.send(phone,
                            String.format("Sorry, only %d units of %s available.",
                                result.getVariant().getStockQuantity(),
                                result.getVariant().getSku()));
                    }
                    case NO_MATCH, VARIANT_NOT_FOUND -> {
                        allMatched = false;
                        twilioMessaging.send(phone,
                            "Product not found: \"" + item.getRawText() + "\"\n" +
                            "Please check the product name.");
                    }
                }
            }

            // 6. Send disambiguation if needed (Phase 3 will manage session state for replies)
            if (!disambiguationLines.isEmpty()) {
                twilioMessaging.send(phone, String.join("\n\n", disambiguationLines));
                return;
            }

            // 7. All matched — show confirmation summary
            if (!confirmationLines.isEmpty() && allMatched) {
                String summary = String.join("\n", confirmationLines) +
                    "\n\nReply *DONE* to generate invoice or keep adding items.";
                twilioMessaging.send(phone, summary);
            }

        } catch (InvalidGeminiResponseException e) {
            log.error("Invalid Gemini response for message '{}': {}", rawMessage, e.getMessage());
            twilioMessaging.send(phone, "System error. Please try again in a moment.");
        } catch (Exception e) {
            log.error("Unexpected error processing message: {}", e.getMessage(), e);
            twilioMessaging.send(phone, "Something went wrong. Please try again!");
        }
    }
}