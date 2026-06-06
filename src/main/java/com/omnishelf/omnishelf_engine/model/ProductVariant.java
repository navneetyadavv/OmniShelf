package com.omnishelf.engine.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
@Data
@NoArgsConstructor
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String color;
    private String size;
    private String storage;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Column(unique = true)
    private String sku;

    // GST compliance: HSN code for the product category
    @Column
    private String hsnCode;

    // GST rate applicable to this product (e.g. 5, 12, 18, 28)
    @Column(nullable = false)
    private BigDecimal gstRatePercent = new BigDecimal("18");
}
