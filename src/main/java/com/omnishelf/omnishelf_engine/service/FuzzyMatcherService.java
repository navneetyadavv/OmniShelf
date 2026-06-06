package com.omnishelf.engine.service;

import com.omnishelf.engine.dto.*;
import com.omnishelf.engine.model.*;
import com.omnishelf.engine.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FuzzyMatcherService {

    private final ProductRepository        productRepo;
    private final ProductVariantRepository variantRepo;

    private static final LevenshteinDistance LD = LevenshteinDistance.getDefaultInstance();
    private static final int BRAND_THRESHOLD   = 3;
    private static final int VARIANT_THRESHOLD = 4;

    public MatchResult match(ParsedItem parsedItem) {
        String inputBrand = parsedItem.getBrand() != null
            ? parsedItem.getBrand()
            : parsedItem.getRawBrand();

        if (inputBrand == null || inputBrand.isBlank()) {
            return MatchResult.noMatch("No brand provided");
        }

        // 1. Try exact brand match first
        List<Product> products = productRepo.findByBrandIgnoreCase(inputBrand);
        boolean wasFuzzy = false;

        // 2. Fall back to Levenshtein fuzzy match
        if (products.isEmpty()) {
            String fuzzyBrand = findClosestBrand(inputBrand);
            if (fuzzyBrand == null) {
                log.info("No brand match for '{}' (fuzzy threshold={})", inputBrand, BRAND_THRESHOLD);
                return MatchResult.noMatch(inputBrand);
            }
            log.info("Fuzzy brand match: '{}' → '{}'", inputBrand, fuzzyBrand);
            products = productRepo.findByBrandIgnoreCase(fuzzyBrand);
            wasFuzzy = true;
        }

        if (products.isEmpty()) return MatchResult.noMatch(inputBrand);

        // 3. For each product, try to match the variant
        ParsedVariant pv = parsedItem.getVariant();
        for (Product product : products) {
            List<ProductVariant> variants = variantRepo.findByProduct(product);
            ProductVariant best = findBestVariant(variants, pv);

            if (best != null) {
                int qty = parsedItem.getQuantity() != null ? parsedItem.getQuantity() : 1;

                if (best.getStockQuantity() < qty) {
                    log.warn("Insufficient stock for {} (req={}, avail={})",
                        best.getSku(), qty, best.getStockQuantity());
                    return MatchResult.insufficientStock(best, qty);
                }

                MatchResult result = MatchResult.found(best, qty);
                result.setWasFuzzyMatch(wasFuzzy);
                result.setOriginalInput(inputBrand);
                result.setSuggestedBrand(best.getProduct().getBrand());
                return result;
            }
        }

        log.info("Brand '{}' found but no variant matched for parsed: {}", inputBrand, pv);
        return MatchResult.variantNotFound(inputBrand);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String findClosestBrand(String input) {
        List<String> brands = productRepo.findAllDistinctBrands();
        String best = null;
        int    bestDist = Integer.MAX_VALUE;

        for (String brand : brands) {
            int dist = LD.apply(input.toLowerCase(), brand.toLowerCase());
            if (dist < bestDist && dist <= BRAND_THRESHOLD) {
                bestDist = dist;
                best     = brand;
            }
        }
        return best;
    }

    private ProductVariant findBestVariant(List<ProductVariant> variants, ParsedVariant pv) {
        if (variants.isEmpty()) return null;
        if (pv == null) return variants.get(0); // no variant info — take first

        // Score each variant; higher = better match
        ProductVariant best = null;
        int bestScore = -1;

        for (ProductVariant v : variants) {
            int score = scoreVariant(v, pv);
            if (score > bestScore) {
                bestScore = score;
                best      = v;
            }
        }

        // Require at least one dimension to match; negative means all mismatched
        return bestScore >= 0 ? best : null;
    }

    private int scoreVariant(ProductVariant v, ParsedVariant pv) {
        int score = 0;

        if (pv.getSize() != null && v.getSize() != null) {
            int dist = LD.apply(pv.getSize().toLowerCase(), v.getSize().toLowerCase());
            score += dist <= 1 ? 3 : -1;
        }

        if (pv.getColor() != null && v.getColor() != null) {
            int dist = LD.apply(pv.getColor().toLowerCase(), v.getColor().toLowerCase());
            score += dist <= VARIANT_THRESHOLD ? 2 : -1;
        }

        if (pv.getStorage() != null && v.getStorage() != null) {
            int dist = LD.apply(pv.getStorage().toLowerCase(), v.getStorage().toLowerCase());
            score += dist <= 1 ? 3 : -1;
        }

        return score;
    }
}
