package com.omnishelf.engine.dto;

import lombok.Data;

@Data
public class ParsedItem {
    private String rawText;
    private String brand;
    private String rawBrand;
    private String category;
    private ParsedVariant variant;
    private Integer quantity;
}
