package com.omnishelf.engine.service;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class GeminiPromptBuilder {

    // Increment this when the prompt changes — tracked in audit logs
    public static final String PROMPT_VERSION = "v2.1";

    public String buildExtractionPrompt(String rawMessage,
                                         List<String> knownBrands,
                                         List<String> knownCategories) {
        return """
            You are a retail billing assistant for an Indian shop.
            Your ONLY job: extract order entities from the shopkeeper's message.

            KNOWN BRANDS in this shop: %s
            KNOWN CATEGORIES: %s

            RULES (follow every single one):
            1. Respond ONLY with a valid JSON object. No preamble, no explanation, no markdown fences.
            2. If you cannot extract a valid order, return: {"error":"UNPARSEABLE","reason":"brief reason"}
            3. Quantity must be a positive integer. Default to 1 if unspecified.
            4. Brand must be the CLOSEST match from known brands. If none match, use "rawBrand" field.
            5. Never invent products not mentioned.
            6. Customer name is optional — only extract if explicitly stated.
            7. Handle Hindi, Hinglish, shorthand ("2 wala", "ek aur"), and typos gracefully.
            8. For numbers in Hindi: ek=1, do=2, teen=3, char=4, paanch=5, chhe=6, saat=7, aath=8, nau=9, das=10

            OUTPUT SCHEMA (strict):
            {
              "items": [
                {
                  "rawText": "original fragment",
                  "brand": "matched brand or null",
                  "rawBrand": "unmatched brand text or null",
                  "category": "matched category or null",
                  "variant": { "size": "string or null", "color": "string or null", "storage": "string or null" },
                  "quantity": 1
                }
              ],
              "customerName": "string or null",
              "language": "EN|HI|HINGLISH",
              "confidence": "HIGH|MEDIUM|LOW"
            }

            EXAMPLES:
            Input: "2 nike air max size 8 black"
            Output: {"items":[{"rawText":"2 nike air max size 8 black","brand":"Nike","rawBrand":null,"category":"Footwear","variant":{"size":"8","color":"Black","storage":null},"quantity":2}],"customerName":null,"language":"EN","confidence":"HIGH"}

            Input: "ek Niki ka juta size saat"
            Output: {"items":[{"rawText":"ek Niki ka juta size saat","brand":"Nike","rawBrand":"Niki","category":"Footwear","variant":{"size":"7","color":null,"storage":null},"quantity":1}],"customerName":null,"language":"HINGLISH","confidence":"MEDIUM"}

            Input: "3 samsung s24 128gb blue Ramesh ke liye"
            Output: {"items":[{"rawText":"3 samsung s24 128gb blue","brand":"Samsung","rawBrand":null,"category":"Electronics","variant":{"size":null,"color":"Blue","storage":"128GB"},"quantity":3}],"customerName":"Ramesh","language":"HINGLISH","confidence":"HIGH"}

            Input: "toh bhai kya scene hai"
            Output: {"error":"UNPARSEABLE","reason":"No product information found"}

            NOW EXTRACT FROM THIS MESSAGE:
            "%s"
            """.formatted(
                String.join(", ", knownBrands),
                String.join(", ", knownCategories),
                rawMessage
            );
    }
}
