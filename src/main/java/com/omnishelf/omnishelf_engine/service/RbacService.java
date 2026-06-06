package com.omnishelf.engine.service;

import com.omnishelf.engine.model.*;
import com.omnishelf.engine.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RbacService {

    private final ShopUserRepository     userRepo;
    private final AuditLogRepository     auditRepo;
    private final TwilioMessagingService twilioMessaging;

    @Transactional
    public boolean canApplyDiscount(String phone, BigDecimal discountAmount) {
        Optional<ShopUser> userOpt = userRepo.findByPhone(phone);
        if (userOpt.isEmpty()) return false;
        ShopUser user = userOpt.get();

        if (user.isOwner()) {
            auditRepo.save(AuditLog.of(phone, AuditAction.DISCOUNT_APPLIED,
                "Discount: ₹" + discountAmount, true));
            log.info("Owner {} applied discount ₹{}", phone, discountAmount);
            return true;
        }

        auditRepo.save(AuditLog.blocked(phone, AuditAction.DISCOUNT_BLOCKED,
            "Staff attempted ₹" + discountAmount + " discount"));
        log.warn("RBAC: Staff {} attempted discount ₹{}", phone, discountAmount);

        twilioMessaging.send(phone,
            "Discounts can only be applied by the shop owner.");

        // Alert all owners
        userRepo.findByRole(UserRole.OWNER).forEach(owner ->
            twilioMessaging.send(owner.getPhone(),
                String.format("Alert: Staff %s attempted ₹%.0f discount — blocked.", phone, discountAmount)));
        return false;
    }

    public boolean isOwner(String phone) {
        return userRepo.findByPhone(phone).map(ShopUser::isOwner).orElse(false);
    }

    public Optional<ShopUser> getUser(String phone) {
        return userRepo.findByPhone(phone);
    }
}
