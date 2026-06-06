package com.omnishelf.engine.service;

import com.omnishelf.engine.model.ProductVariant;
import com.omnishelf.engine.model.UserRole;
import com.omnishelf.engine.repository.ProductVariantRepository;
import com.omnishelf.engine.repository.ShopUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class LowStockAlertService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final ProductVariantRepository variantRepo;
    private final ShopUserRepository       userRepo;
    private final TwilioMessagingService   twilioMessaging;

    /**
     * Runs every day at 8 AM. Sends a low-stock summary to all owners.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void sendLowStockAlert() {
        List<ProductVariant> lowStock = variantRepo.findLowStockVariants(LOW_STOCK_THRESHOLD);
        if (lowStock.isEmpty()) return;

        StringBuilder msg = new StringBuilder("⚠ *Low Stock Alert*\n\n");
        for (ProductVariant v : lowStock) {
            msg.append(String.format("• %s %s %s — *%d left*\n",
                v.getProduct().getBrand(),
                v.getProduct().getName(),
                buildVariantNote(v),
                v.getStockQuantity()));
        }
        msg.append("\nRestock these items soon.");

        log.info("Sending low-stock alert: {} item(s) below threshold", lowStock.size());
        userRepo.findByRole(UserRole.OWNER).forEach(owner ->
            twilioMessaging.send(owner.getPhone(), msg.toString()));
    }

    private String buildVariantNote(ProductVariant v) {
        StringBuilder sb = new StringBuilder();
        if (v.getColor()   != null) sb.append(v.getColor()).append(" ");
        if (v.getSize()    != null) sb.append("Sz").append(v.getSize()).append(" ");
        if (v.getStorage() != null) sb.append(v.getStorage());
        return sb.toString().trim();
    }
}
