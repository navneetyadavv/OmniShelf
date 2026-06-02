package com.omnishelf.omnishelf_engine.config;

import com.omnishelf.omnishelf_engine.model.Product;
import com.omnishelf.omnishelf_engine.model.ProductVariant;
import com.omnishelf.omnishelf_engine.repository.ProductRepository;
import com.omnishelf.omnishelf_engine.repository.ProductVariantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository        productRepo;
    private final ProductVariantRepository variantRepo;

    public DataInitializer(ProductRepository productRepo,
                           ProductVariantRepository variantRepo) {
        this.productRepo = productRepo;
        this.variantRepo = variantRepo;
    }

    @Override
    public void run(String... args) {
        if (productRepo.count() > 0) {
            log.info("DB already seeded — skipping DataInitializer");
            return;
        }

        log.info("Seeding database with sample products...");

        // ── Nike ──────────────────────────────────────────────────────
        Product nike = saveProduct("Nike", "Air Max", "Footwear");
        saveVariant(nike, "Black", "7",  null, new BigDecimal("8999"),  15, "NIKE-AIRMAX-BLACK-7");
        saveVariant(nike, "Black", "8",  null, new BigDecimal("8999"),  12, "NIKE-AIRMAX-BLACK-8");
        saveVariant(nike, "Black", "9",  null, new BigDecimal("8999"),   8, "NIKE-AIRMAX-BLACK-9");
        saveVariant(nike, "Red",   "8",  null, new BigDecimal("8999"),   5, "NIKE-AIRMAX-RED-8");
        saveVariant(nike, "White", "8",  null, new BigDecimal("9499"),  10, "NIKE-AIRMAX-WHITE-8");

        // ── Adidas ────────────────────────────────────────────────────
        Product adidas = saveProduct("Adidas", "Ultraboost", "Footwear");
        saveVariant(adidas, "Blue",  "8", null, new BigDecimal("7499"),  8, "ADIDAS-ULTRA-BLUE-8");
        saveVariant(adidas, "White", "9", null, new BigDecimal("7499"),  6, "ADIDAS-ULTRA-WHITE-9");

        // ── Samsung ───────────────────────────────────────────────────
        Product samsung = saveProduct("Samsung", "Galaxy S24", "Electronics");
        saveVariant(samsung, "Blue",  null, "128GB", new BigDecimal("74999"), 3, "SAMSUNG-S24-128-BLUE");
        saveVariant(samsung, "Black", null, "256GB", new BigDecimal("84999"), 4, "SAMSUNG-S24-256-BLACK");

        // ── Apple ─────────────────────────────────────────────────────
        Product apple = saveProduct("Apple", "Watch SE", "Electronics");
        saveVariant(apple, "Silver", null, null, new BigDecimal("29999"), 5, "APPLE-WATCH-SE-SILVER");
        saveVariant(apple, "Black",  null, null, new BigDecimal("29999"), 3, "APPLE-WATCH-SE-BLACK");

        // ── Bata ──────────────────────────────────────────────────────
        Product bata = saveProduct("Bata", "Chappal", "Footwear");
        saveVariant(bata, "Red",   "9",  null, new BigDecimal("899"), 20, "BATA-CHAPPAL-RED-9");
        saveVariant(bata, "Brown", "10", null, new BigDecimal("899"), 15, "BATA-CHAPPAL-BROWN-10");

        // ── Puma ──────────────────────────────────────────────────────
        Product puma = saveProduct("Puma", "Suede", "Footwear");
        saveVariant(puma, "Red",  "8", null, new BigDecimal("6999"), 7, "PUMA-SUEDE-RED-8");
        saveVariant(puma, "Navy", "9", null, new BigDecimal("6999"), 5, "PUMA-SUEDE-NAVY-9");

        log.info("Database seeded successfully with {} products",
            productRepo.count());
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private Product saveProduct(String brand, String name, String category) {
        Product p = new Product();
        p.setBrand(brand);
        p.setName(name);
        p.setCategory(category);
        return productRepo.save(p);
    }

    private void saveVariant(Product product, String color, String size,
                              String storage, BigDecimal price,
                              int stock, String sku) {
        ProductVariant v = new ProductVariant();
        v.setProduct(product);
        v.setColor(color);
        v.setSize(size);
        v.setStorage(storage);
        v.setPrice(price);
        v.setStockQuantity(stock);
        v.setSku(sku);
        variantRepo.save(v);
    }
}