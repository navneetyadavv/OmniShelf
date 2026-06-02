package com.omnishelf.omnishelf_engine.service;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class GeminiPromptBuilder {

    public String buildExtractionPrompt(String rawMessage, List<String> knownBrands, List<String> knownCategories) {
        return """
            You are a retail billing assistant for an Indian shop.
            Your ONLY job is to extract order entities from the shopkeeper's message.

            KNOWN BRANDS in this shop's inventory: %s
            KNOWN CATEGORIES: %s

            RULES (follow every single one):
            1. Respond ONLY with a valid JSON object. No preamble, no explanation, no markdown.
            2. If you cannot extract a valid order, return: {"error": "UNPARSEABLE", "reason": "brief reason"}
            3. Quantity must be a positive integer. If not specified, default to 1.
            4. Brand and category must be the CLOSEST match from known brands/categories above.
               If no close match exists, return the raw text as-is in a "rawBrand" field instead.
            5. Never invent products that weren't mentioned.
            6. Customer name is optional — only extract if explicitly stated.
            7. Handle Hindi, Hinglish, shorthand (e.g. "2 wala", "ek aur"), and typos gracefully.

            OUTPUT SCHEMA (strict):
            {
              "items": [
                {
                  "rawText": "original fragment for this item",
                  "brand": "matched brand or null",
                  "rawBrand": "unmatched brand text or null",
                  "category": "matched category or null",
                  "variant": {
                    "size": "string or null",
                    "color": "string or null",
                    "storage": "string or null"
                  },
                  "quantity": 1
                }
              ],
              "customerName": "string or null",
              "language": "EN|HI|HINGLISH",
              "confidence": "HIGH|MEDIUM|LOW"
            }

            EXAMPLES:

            Input: "2 nike air max size 8 black"
            Output: {"items":[{"rawText":"2 nike air max size 8 black","brand":"Nike","rawBrand":null,
            "category":"Footwear","variant":{"size":"8","color":"Black","storage":null},"quantity":2}],
            "customerName":null,"language":"EN","confidence":"HIGH"}

            Input: "ek Niki ka juta size saat leke do"
            Output: {"items":[{"rawText":"ek Niki ka juta size saat","brand":"Nike","rawBrand":"Niki",
            "category":"Footwear","variant":{"size":"7","color":null,"storage":null},"quantity":1}],
            "customerName":null,"language":"HINGLISH","confidence":"MEDIUM"}

            Input: "3 samsung s24 128gb blue Ramesh ke liye"
            Output: {"items":[{"rawText":"3 samsung s24 128gb blue","brand":"Samsung","rawBrand":null,
            "category":"Electronics","variant":{"size":null,"color":"Blue","storage":"128GB"},"quantity":3}],
            "customerName":"Ramesh","language":"HINGLISH","confidence":"HIGH"}

            Input: "toh bhai kya scene hai"
            Output: {"error":"UNPARSEABLE","reason":"No product information found in message"}

            NOW EXTRACT FROM THIS MESSAGE:
            "%s"
            """.formatted(
                String.join(", ", knownBrands),
                String.join(", ", knownCategories),
                rawMessage
            );
    }
}