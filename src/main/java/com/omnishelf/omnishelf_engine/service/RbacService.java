package com.billing.service;

import com.billing.model.*;
import com.billing.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@Slf4j
public class RbacService {

    private final ShopUserRepository   userRepo;
    private final AuditLogRepository   auditRepo;
    private final TwilioMessagingService twilioMessaging;

    public RbacService(ShopUserRepository userRepo,
                       AuditLogRepository auditRepo,
                       TwilioMessagingService twilioMessaging) {
        this.userRepo        = userRepo;
        this.auditRepo       = auditRepo;
        this.twilioMessaging = twilioMessaging;
    }

    /**
     * Checks if a phone number can apply a discount.
     * OWNER: allowed — logs it.
     * STAFF: blocked — logs attempt and alerts owner.
     */
    @Transactional
    public boolean canApplyDiscount(String phone, BigDecimal discountAmount) {
        Optional<ShopUser> userOpt = userRepo.findByPhone(phone);
        if (userOpt.isEmpty()) return false;

        ShopUser user = userOpt.get();

        if (user.isOwner()) {
            auditRepo.save(AuditLog.of(phone, AuditAction.DISCOUNT_APPLIED,
                "Discount: ₹" + discountAmount, true));
            log.info("Owner {} applied discount of ₹{}", phone, discountAmount);
            return true;
        }

        // Staff attempted discount — block and alert owner
        auditRepo.save(AuditLog.blocked(phone,
            AuditAction.DISCOUNT_BLOCKED,
            "Staff attempted ₹" + discountAmount + " discount"));

        log.warn("RBAC: Staff {} attempted to apply discount of ₹{}",
            phone, discountAmount);

        twilioMessaging.send(phone,
            "Discounts can only be applied by the shop owner.\n" +
            "The item has been added at the standard price.");

        // Alert owner asynchronously
        notifyOwnerOfDiscountAttempt(phone, discountAmount);
        return false;
    }

    /**
     * Returns the ShopUser for a given phone — used by other services
     * to check role without going to the DB themselves.
     */
    public Optional<ShopUser> getUser(String phone) {
        return userRepo.findByPhone(phone);
    }

    public boolean isOwner(String phone) {
        return userRepo.findByPhone(phone)
            .map(ShopUser::isOwner)
            .orElse(false);
    }

    private void notifyOwnerOfDiscountAttempt(String staffPhone,
                                               BigDecimal amount) {
        // Find the owner and alert them
        userRepo.findAll().stream()
            .filter(ShopUser::isOwner)
            .forEach(owner -> twilioMessaging.send(owner.getPhone(),
                String.format(
                    "Alert: Staff number %s attempted to apply a " +
                    "₹%.0f discount and was blocked.",
                    staffPhone, amount)));
    }
}