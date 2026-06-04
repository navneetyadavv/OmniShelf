package com.billing.service;

import com.billing.model.*;
import com.billing.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class RateLimiterService {

    // From your project doc: 20 bills in 5 minutes = auto-lock
    private static final int    BURST_LIMIT        = 20;
    private static final int    BURST_WINDOW_MINS  = 5;
    private static final int    DAILY_LIMIT        = 500; // sanity cap

    private final ShopUserRepository  userRepo;
    private final AuditLogRepository  auditRepo;
    private final TwilioMessagingService twilioMessaging;

    public RateLimiterService(ShopUserRepository userRepo,
                               AuditLogRepository auditRepo,
                               TwilioMessagingService twilioMessaging) {
        this.userRepo        = userRepo;
        this.auditRepo       = auditRepo;
        this.twilioMessaging = twilioMessaging;
    }

    /**
     * Called before every bill confirmation.
     * Returns true if the action is allowed, false if it should be blocked.
     */
    @Transactional
    public boolean isAllowed(String phone) {
        Optional<ShopUser> userOpt = userRepo.findByPhone(phone);
        if (userOpt.isEmpty()) return false;

        ShopUser user = userOpt.get();

        // Already locked
        if (user.isLocked()) {
            log.warn("Rate limit: blocked request from locked account {}", phone);
            twilioMessaging.send(phone,
                "Your account is locked. Contact the shop owner.");
            return false;
        }

        // Check burst rate: bills in last 5 minutes
        LocalDateTime burstWindow = LocalDateTime.now()
            .minusMinutes(BURST_WINDOW_MINS);
        long recentBills = auditRepo
            .countConfirmedBillsSince(phone, burstWindow);

        if (recentBills >= BURST_LIMIT) {
            lockAccount(user, String.format(
                "Burst limit: %d bills in %d minutes",
                recentBills, BURST_WINDOW_MINS));
            return false;
        }

        // Daily sanity cap
        if (user.getBillsGeneratedToday() >= DAILY_LIMIT) {
            lockAccount(user, "Daily limit of " + DAILY_LIMIT + " bills exceeded");
            return false;
        }

        // Allowed — increment counter
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

        auditRepo.save(AuditLog.of(user.getPhone(),
            AuditAction.ACCOUNT_LOCKED, reason, false));

        log.warn("Account locked: {} — reason: {}", user.getPhone(), reason);

        // Alert owner on a secondary channel
        notifyOwnerOfLock(user, reason);

        twilioMessaging.send(user.getPhone(),
            "Your account has been temporarily locked due to unusual activity.\n" +
            "The shop owner has been notified.");
    }

    @Transactional
    public boolean unlockAccount(String ownerPhone, String targetPhone) {
        if (!isOwner(ownerPhone)) {
            twilioMessaging.send(ownerPhone,
                "Only the shop owner can unlock accounts.");
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

        auditRepo.save(AuditLog.of(ownerPhone,
            AuditAction.ACCOUNT_UNLOCKED,
            "Unlocked: " + targetPhone, true));

        twilioMessaging.send(targetPhone,
            "Your account has been unlocked by the shop owner. " +
            "You can continue billing.");
        twilioMessaging.send(ownerPhone,
            "Account " + targetPhone + " has been unlocked.");

        log.info("Account {} unlocked by owner {}", targetPhone, ownerPhone);
        return true;
    }

    // Reset daily counters at midnight every day
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void resetDailyCounters() {
        userRepo.resetDailyCounters();
        log.info("Daily rate limit counters reset");
    }

    private boolean isOwner(String phone) {
        return userRepo.findByPhone(phone)
            .map(ShopUser::isOwner)
            .orElse(false);
    }

    private void notifyOwnerOfLock(ShopUser lockedUser, String reason) {
        userRepo.findAll().stream()
            .filter(ShopUser::isOwner)
            .forEach(owner -> twilioMessaging.send(owner.getPhone(),
                String.format(
                    "Security alert: Account %s has been auto-locked.\n" +
                    "Reason: %s\n\n" +
                    "Reply *UNLOCK %s* to restore access.",
                    lockedUser.getPhone(), reason, lockedUser.getPhone())));
    }
}