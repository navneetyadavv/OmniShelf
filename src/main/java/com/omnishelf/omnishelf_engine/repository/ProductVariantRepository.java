package com.omnishelf.omnishelf_engine.repository;

import com.omnishelf.omnishelf_engine.model.Product;
import com.omnishelf.omnishelf_engine.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {

    List<ProductVariant> findByProduct(Product product);

    Optional<ProductVariant> findBySku(String sku);
}