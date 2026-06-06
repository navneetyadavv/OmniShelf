package com.omnishelf.engine.config;

import com.omnishelf.engine.model.*;
import com.omnishelf.engine.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository        productRepo;
    private final ProductVariantRepository variantRepo;
    private final ShopUserRepository       userRepo;

    @Override
    public void run(String... args) {
        seedUsers();
        seedProducts();
    }

    private void seedUsers() {
        if (userRepo.count() > 0) return;

        // Owner — change phone number to your real WhatsApp number
        ShopUser owner = new ShopUser();
        owner.setPhone("+919876543210");
        owner.setName("Shop Owner");
        owner.setRole(UserRole.OWNER);
        userRepo.save(owner);

        // Staff member
        ShopUser staff = new ShopUser();
        staff.setPhone("+919876543211");
        staff.setName("Staff 1");
        staff.setRole(UserRole.STAFF);
        userRepo.save(staff);

        log.info("Seeded 1 owner + 1 staff user");
    }

    private void seedProducts() {
        if (productRepo.count() > 0) {
            log.info("DB already seeded — skipping products");
            return;
        }
        log.info("Seeding products...");

        // Footwear HSN: 6402 (rubber/plastics), GST 18%
        Product nike = save("Nike", "Air Max", "Footwear");
        variant(nike, "Black", "7",  null, "8999",  15, "NIKE-AIRMAX-BLACK-7",  "6402");
        variant(nike, "Black", "8",  null, "8999",  12, "NIKE-AIRMAX-BLACK-8",  "6402");
        variant(nike, "Black", "9",  null, "8999",   8, "NIKE-AIRMAX-BLACK-9",  "6402");
        variant(nike, "Red",   "8",  null, "8999",   5, "NIKE-AIRMAX-RED-8",    "6402");
        variant(nike, "White", "8",  null, "9499",  10, "NIKE-AIRMAX-WHITE-8",  "6402");

        Product adidas = save("Adidas", "Ultraboost", "Footwear");
        variant(adidas, "Blue",  "8", null, "7499",  8, "ADIDAS-ULTRA-BLUE-8",  "6402");
        variant(adidas, "White", "9", null, "7499",  6, "ADIDAS-ULTRA-WHITE-9", "6402");

        Product puma = save("Puma", "Suede", "Footwear");
        variant(puma, "Red",  "8", null, "6999", 7, "PUMA-SUEDE-RED-8",  "6402");
        variant(puma, "Navy", "9", null, "6999", 5, "PUMA-SUEDE-NAVY-9", "6402");

        Product bata = save("Bata", "Chappal", "Footwear");
        variant(bata, "Red",   "9",  null, "899", 20, "BATA-CHAPPAL-RED-9",   "6402");
        variant(bata, "Brown", "10", null, "899", 15, "BATA-CHAPPAL-BROWN-10","6402");

        // Electronics HSN: 8517 (phones), GST 18%
        Product samsung = save("Samsung", "Galaxy S24", "Electronics");
        variant(samsung, "Blue",  null, "128GB", "74999", 3, "SAMSUNG-S24-128-BLUE",  "8517");
        variant(samsung, "Black", null, "256GB", "84999", 4, "SAMSUNG-S24-256-BLACK", "8517");

        Product apple = save("Apple", "Watch SE", "Electronics");
        variant(apple, "Silver", null, null, "29999", 5, "APPLE-WATCH-SE-SILVER", "8517");
        variant(apple, "Black",  null, null, "29999", 3, "APPLE-WATCH-SE-BLACK",  "8517");

        log.info("Seeded {} products", productRepo.count());
    }

    private Product save(String brand, String name, String category) {
        Product p = new Product();
        p.setBrand(brand); p.setName(name); p.setCategory(category);
        return productRepo.save(p);
    }

    private void variant(Product product, String color, String size, String storage,
                         String price, int stock, String sku, String hsn) {
        ProductVariant v = new ProductVariant();
        v.setProduct(product); v.setColor(color); v.setSize(size);
        v.setStorage(storage); v.setPrice(new BigDecimal(price));
        v.setStockQuantity(stock); v.setSku(sku); v.setHsnCode(hsn);
        v.setGstRatePercent(new BigDecimal("18"));
        variantRepo.save(v);
    }
}
