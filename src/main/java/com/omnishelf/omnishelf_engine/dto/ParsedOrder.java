package com.omnishelf.omnishelf_engine.dto;

import lombok.Data;
import java.util.List;

@Data
public class ParsedOrder {
    private List<ParsedItem> items;
    private String customerName;
    private String language;         // EN | HI | HINGLISH
    private String confidence;       // HIGH | MEDIUM | LOW
    private String error;            // set if UNPARSEABLE
    private String reason;
}