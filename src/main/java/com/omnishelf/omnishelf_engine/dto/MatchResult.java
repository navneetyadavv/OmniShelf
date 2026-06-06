package com.omnishelf.engine.dto;

import com.omnishelf.engine.model.ProductVariant;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MatchResult {

    public enum Status { FOUND, FUZZY_MATCH, VARIANT_NOT_FOUND, NO_MATCH, INSUFFICIENT_STOCK }

    private Status         status;
    private ProductVariant variant;
    private Integer        requestedQuantity;
    private boolean        wasFuzzyMatch;
    private String         originalInput;
    private String         suggestedBrand;
    private String         errorDetail;

    public static MatchResult found(ProductVariant v, int qty) {
        MatchResult r = new MatchResult();
        r.status = Status.FOUND; r.variant = v; r.requestedQuantity = qty;
        return r;
    }

    public static MatchResult noMatch(String input) {
        MatchResult r = new MatchResult();
        r.status = Status.NO_MATCH; r.errorDetail = input;
        return r;
    }

    public static MatchResult variantNotFound(String brand) {
        MatchResult r = new MatchResult();
        r.status = Status.VARIANT_NOT_FOUND;
        r.errorDetail = "Brand found but variant not matched: " + brand;
        return r;
    }

    public static MatchResult insufficientStock(ProductVariant v, int qty) {
        MatchResult r = new MatchResult();
        r.status = Status.INSUFFICIENT_STOCK; r.variant = v; r.requestedQuantity = qty;
        return r;
    }
}
