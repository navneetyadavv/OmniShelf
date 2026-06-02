package com.omnishelf.omnishelf_engine.dto;

import lombok.Data;

@Data
public class ParsedItem {
    private String rawText;
    private String brand;            // matched from known brands
    private String rawBrand;         // unmatched — triggers fuzzy search
    private String category;
    private ParsedVariant variant;
    private Integer quantity;
}