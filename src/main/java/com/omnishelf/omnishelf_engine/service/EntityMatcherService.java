package com.omnishelf.omnishelf_engine.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.apache.commons.text.similarity.LevenshteinDistance;
import com.omnishelf.omnishelf_engine.model.Product;
import com.omnishelf.omnishelf_engine.model.ProductVariant;
import com.omnishelf.omnishelf_engine.repository.ProductRepository;
import com.omnishelf.omnishelf_engine.repository.ProductVariantRepository;
import com.omnishelf.omnishelf_engine.dto.MatchResult;
import com.omnishelf.omnishelf_engine.dto.ParsedItem;
import com.omnishelf.omnishelf_engine.dto.ParsedVariant;

@Service
@Slf4j
public class EntityMatcherService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;

    // Levenshtein similarity threshold: 0.0 = nothing alike, 1.0 = identical
    private static final double MATCH_THRESHOLD = 0.65;

    public EntityMatcherService(ProductRepository productRepository,
                                 ProductVariantRepository variantRepository) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
    }

    /**
     * Takes a parsed item (brand may be null/typo'd) and finds the best
     * matching ProductVariant in the DB. Returns a MatchResult.
     */
    public MatchResult matchItem(ParsedItem item) {
        String searchBrand = item.getBrand() != null ? item.getBrand() : item.getRawBrand();

        // Step 1: try exact brand match first (fast path)
        List<Product> exactMatches = productRepository.findByBrandIgnoreCase(searchBrand);
        if (!exactMatches.isEmpty()) {
            return matchVariant(exactMatches, item);
        }

        // Step 2: fuzzy brand match against all brands in DB
        List<String> allBrands = productRepository.findAllDistinctBrands();
        String bestBrand = findBestFuzzyMatch(searchBrand, allBrands);

        if (bestBrand == null) {
            log.warn("No fuzzy match found for brand: {}", searchBrand);
            return MatchResult.noMatch(searchBrand);
        }

        log.info("Fuzzy match: '{}' → '{}'", searchBrand, bestBrand);
        List<Product> fuzzyMatches = productRepository.findByBrandIgnoreCase(bestBrand);
        MatchResult result = matchVariant(fuzzyMatches, item);
        result.setWasFuzzyMatch(true);
        result.setOriginalInput(searchBrand);
        result.setSuggestedBrand(bestBrand);
        return result;
    }

    private MatchResult matchVariant(List<Product> products, ParsedItem item) {
        for (Product product : products) {
            List<ProductVariant> variants = variantRepository.findByProduct(product);

            for (ProductVariant variant : variants) {
                if (variantMatches(variant, item.getVariant())) {
                    if (variant.getStockQuantity() < item.getQuantity()) {
                        return MatchResult.insufficientStock(variant, item.getQuantity());
                    }
                    return MatchResult.found(variant, item.getQuantity());
                }
            }
        }
        return MatchResult.variantNotFound(products.get(0).getBrand());
    }

    private boolean variantMatches(ProductVariant dbVariant, ParsedVariant parsed) {
        if (parsed == null) return true; // no variant specified, take first

        boolean sizeMatch = parsed.getSize() == null
            || fuzzyEquals(dbVariant.getSize(), parsed.getSize());

        boolean colorMatch = parsed.getColor() == null
            || fuzzyEquals(dbVariant.getColor(), parsed.getColor());

        boolean storageMatch = parsed.getStorage() == null
            || fuzzyEquals(dbVariant.getSize(), parsed.getStorage()); // size field covers storage

        return sizeMatch && colorMatch && storageMatch;
    }

    /**
     * Core fuzzy matching using Levenshtein similarity.
     * "Niki" vs "Nike" → ~0.75 similarity → match
     * "Nike" vs "Adidas" → ~0.22 → no match
     */
    public String findBestFuzzyMatch(String input, List<String> candidates) {
        if (input == null || candidates == null || candidates.isEmpty()) return null;

        LevenshteinDistance levenshtein = new LevenshteinDistance();
        String bestMatch = null;
        double bestScore = 0.0;

        String normalizedInput = input.toLowerCase().trim();

        for (String candidate : candidates) {
            String normalizedCandidate = candidate.toLowerCase().trim();
            int distance = levenshtein.apply(normalizedInput, normalizedCandidate);
            int maxLen = Math.max(normalizedInput.length(), normalizedCandidate.length());
            double similarity = 1.0 - ((double) distance / maxLen);

            if (similarity > bestScore) {
                bestScore = similarity;
                bestMatch = candidate;
            }
        }

        return bestScore >= MATCH_THRESHOLD ? bestMatch : null;
    }

    private boolean fuzzyEquals(String a, String b) {
        if (a == null || b == null) return false;
        double threshold = 0.75; // stricter for variant fields
        LevenshteinDistance lev = new LevenshteinDistance();
        String na = a.toLowerCase().trim();
        String nb = b.toLowerCase().trim();
        int dist = lev.apply(na, nb);
        int maxLen = Math.max(na.length(), nb.length());
        return maxLen == 0 || (1.0 - (double) dist / maxLen) >= threshold;
    }
}