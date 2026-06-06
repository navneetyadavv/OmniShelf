package com.omnishelf.engine.repository;

import com.omnishelf.engine.model.Product;
import com.omnishelf.engine.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {

    List<ProductVariant> findByProduct(Product product);

    Optional<ProductVariant> findBySku(String sku);

    @Query("SELECT v FROM ProductVariant v WHERE v.stockQuantity <= :threshold ORDER BY v.stockQuantity ASC")
    List<ProductVariant> findLowStockVariants(int threshold);
}
