package com.omnishelf.omnishelf_engine.repository;

import com.omnishelf.omnishelf_engine.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, String> {

    List<Product> findByBrandIgnoreCase(String brand);

    @Query("SELECT DISTINCT p.brand FROM Product p ORDER BY p.brand")
    List<String> findAllDistinctBrands();

    @Query("SELECT DISTINCT p.category FROM Product p ORDER BY p.category")
    List<String> findAllDistinctCategories();
}