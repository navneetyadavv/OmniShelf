package com.omnishelf.engine.service;

import com.omnishelf.engine.dto.*;
import com.omnishelf.engine.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

/**
 * Offline / degraded-mode parser.
 * Handles simple patterns like "2 Nike Air 42" or "Samsung 128gb x3"
 * without calling any external API. Used when Gemini is unreachable.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FallbackNlpParserService {

    private final ProductRepository productRepo;

    private static final Map<String, Integer> HINDI_NUMS = new LinkedHashMap<>() {{
        put("ek", 1); put("do", 2); put("teen", 3); put("char", 4);
        put("paanch", 5); put("chhe", 6); put("saat", 7); put("aath", 8);
        put("nau", 9); put("das", 10);
    }};

    // Matches: optional quantity, brand, optional size/storage, optional "x qty"
    private static final Pattern ITEM_PATTERN = Pattern.compile(
        "(?:(\\d+|" + String.join("|", Map.of("ek","1","do","2","teen","3").keySet()) + ")\\s+)?" +
        "([a-zA-Z][a-zA-Z0-9 ]+?)\\s*" +
        "(?:(\\d+(?:gb|tb|ml)?|size\\s*\\d+)\\s*)?" +
        "(?:x\\s*(\\d+))?",
        Pattern.CASE_INSENSITIVE
    );

    public ParsedOrder parse(String rawMessage) {
        log.info("Using FALLBACK NLP parser for: {}", rawMessage);
        ParsedOrder order = new ParsedOrder();
        order.setLanguage("EN");
        order.setConfidence("LOW");

        List<String> knownBrands = productRepo.findAllDistinctBrands();

        // Split by comma or "and" or newline
        String[] segments = rawMessage.split("[,\n]|\\band\\b");
        List<com.omnishelf.engine.dto.ParsedItem> items = new ArrayList<>();

        for (String segment : segments) {
            segment = segment.trim();
            if (segment.isBlank()) continue;

            ParsedItem item = tryParse(segment, knownBrands);
            if (item != null) items.add(item);
        }

        order.setItems(items);

        if (items.isEmpty()) {
            order.setError("UNPARSEABLE");
            order.setReason("Fallback parser could not extract any products");
        }

        return order;
    }

    private ParsedItem tryParse(String segment, List<String> knownBrands) {
        // Normalise Hindi numbers
        String normalized = segment.toLowerCase();
        for (Map.Entry<String, Integer> e : HINDI_NUMS.entrySet()) {
            normalized = normalized.replaceAll("\\b" + e.getKey() + "\\b", e.getValue().toString());
        }

        // Try to find a known brand in the segment
        String matchedBrand = null;
        for (String brand : knownBrands) {
            if (normalized.contains(brand.toLowerCase())) {
                matchedBrand = brand;
                break;
            }
        }

        if (matchedBrand == null) return null;

        ParsedItem item = new ParsedItem();
        item.setRawText(segment);
        item.setBrand(matchedBrand);
        item.setQuantity(1);

        // Extract quantity
        Pattern qtyPattern = Pattern.compile("(?:^|\\s)(\\d+)\\s+" + matchedBrand, Pattern.CASE_INSENSITIVE);
        Matcher qtyMatcher = qtyPattern.matcher(normalized);
        if (qtyMatcher.find()) {
            item.setQuantity(Integer.parseInt(qtyMatcher.group(1)));
        }

        // Trailing x2 pattern
        Pattern xQty = Pattern.compile("x\\s*(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);
        Matcher xMatcher = xQty.matcher(normalized);
        if (xMatcher.find()) item.setQuantity(Integer.parseInt(xMatcher.group(1)));

        // Extract size
        ParsedVariant variant = new ParsedVariant();
        Pattern sizePattern = Pattern.compile("(?:size\\s*)?(\\b\\d{1,2}\\b)(?!gb|tb)");
        Matcher sizeMatcher = sizePattern.matcher(normalized);
        if (sizeMatcher.find()) variant.setSize(sizeMatcher.group(1));

        // Extract storage
        Pattern storagePattern = Pattern.compile("(\\d+(?:gb|tb))", Pattern.CASE_INSENSITIVE);
        Matcher storageMatcher = storagePattern.matcher(normalized);
        if (storageMatcher.find()) variant.setStorage(storageMatcher.group(1).toUpperCase());

        item.setVariant(variant);
        return item;
    }
}
