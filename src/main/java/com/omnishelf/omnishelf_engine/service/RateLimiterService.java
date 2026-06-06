package com.omnishelf.engine.service;

import com.omnishelf.engine.model.*;
import com.omnishelf.engine.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimiterService {

    @Value("${ratelimit.burst-limit:20}")
    private int burstLimit;

    @Value("${ratelimit.burst-window-minutes:5}")
    private int burstWindowMinutes;

    @Value("${ratelimit.daily-limit:500}")
    private int dailyLimit;

    private final ShopUserRepository     userRepo;
    private final AuditLogRepository     auditRepo;
    private final TwilioMessagingService twilioMessaging;

    @Transactional
    public boolean isAllowed(String phone) {
        Optional<ShopUser> userOpt = userRepo.findByPhone(phone);
        if (userOpt.isEmpty()) return false;
        ShopUser user = userOpt.get();

        if (user.isLocked()) {
            twilioMessaging.send(phone, "Your account is locked. Contact the shop owner.");
            return false;
        }

        // Burst check
        LocalDateTime burstWindow = LocalDateTime.now().minusMinutes(burstWindowMinutes);
        long recentBills = auditRepo.countConfirmedBillsSince(phone, burstWindow);

        if (recentBills >= burstLimit) {
            lockAccount(user, String.format("Burst limit: %d bills in %d minutes", recentBills, burstWindowMinutes));
            return false;
        }

        // Daily cap
        if (user.getBillsGeneratedToday() >= dailyLimit) {
            lockAccount(user, "Daily limit of " + dailyLimit + " bills exceeded");
            return false;
        }

        user.setBillsGeneratedToday(user.getBillsGeneratedToday() + 1);
        user.setLastBillAt(LocalDateTime.now());
        userRepo.save(user);
        return true;
    }

    @Transactional
    public void lockAccount(ShopUser user, String reason) {
        user.setLocked(true);
        user.setLockedAt(LocalDateTime.now());
        user.setLockReason(reason);
        userRepo.save(user);

        auditRepo.save(AuditLog.of(user.getPhone(), AuditAction.ACCOUNT_LOCKED, reason, false));
        log.warn("Account locked: {} — {}", user.getPhone(), reason);

        // Alert all owners
        userRepo.findByRole(UserRole.OWNER).forEach(owner ->
            twilioMessaging.send(owner.getPhone(),
                String.format("🔒 Security alert: %s auto-locked.\nReason: %s\n\nReply *UNLOCK %s* to restore.",
                    user.getPhone(), reason, user.getPhone())));

        twilioMessaging.send(user.getPhone(),
            "Your account has been temporarily locked due to unusual activity.\nThe shop owner has been notified.");
    }

    @Transactional
    public boolean unlockAccount(String ownerPhone, String targetPhone) {
        if (!userRepo.findByPhone(ownerPhone).map(ShopUser::isOwner).orElse(false)) {
            twilioMessaging.send(ownerPhone, "Only the shop owner can unlock accounts.");
            return false;
        }

        Optional<ShopUser> targetOpt = userRepo.findByPhone(targetPhone);
        if (targetOpt.isEmpty()) {
            twilioMessaging.send(ownerPhone, "User not found: " + targetPhone);
            return false;
        }

        ShopUser target = targetOpt.get();
        target.setLocked(false);
        target.setLockedAt(null);
        target.setLockReason(null);
        userRepo.save(target);

        auditRepo.save(AuditLog.of(ownerPhone, AuditAction.ACCOUNT_UNLOCKED, "Unlocked: " + targetPhone, true));
        twilioMessaging.send(targetPhone, "✓ Your account has been unlocked. You can continue billing.");
        twilioMessaging.send(ownerPhone, "Account " + targetPhone + " unlocked.");
        log.info("Account {} unlocked by {}", targetPhone, ownerPhone);
        return true;
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void resetDailyCounters() {
        userRepo.resetDailyCounters();
        log.info("Daily rate limit counters reset");
    }
}
