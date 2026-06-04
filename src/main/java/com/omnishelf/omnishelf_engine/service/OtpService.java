package com.billing.service;

import com.billing.model.AuditAction;
import com.billing.model.AuditLog;
import com.billing.model.ShopUser;
import com.billing.repository.AuditLogRepository;
import com.billing.repository.ShopUserRepository;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class OtpService {

    @Value("${twilio.verify-service-sid}")
    private String verifyServiceSid;

    private final ShopUserRepository   userRepo;
    private final AuditLogRepository   auditRepo;
    private final TwilioMessagingService twilioMessaging;

    public OtpService(ShopUserRepository userRepo,
                      AuditLogRepository auditRepo,
                      TwilioMessagingService twilioMessaging) {
        this.userRepo        = userRepo;
        this.auditRepo       = auditRepo;
        this.twilioMessaging = twilioMessaging;
    }

    /**
     * Checks if the phone is registered and verified for today.
     * If not verified, triggers OTP and returns false.
     * If unregistered, sends an informative message.
     */
    public boolean isAuthorised(String phone) {
        Optional<ShopUser> userOpt = userRepo.findByPhone(phone);

        // Unregistered number — reject silently (don't reveal system info)
        if (userOpt.isEmpty()) {
            log.warn("Unregistered number attempted access: {}", phone);
            twilioMessaging.send(phone,
                "This number is not registered in the billing system.\n" +
                "Please contact the shop owner.");
            return false;
        }

        ShopUser user = userOpt.get();

        // Locked account
        if (user.isLocked()) {
            twilioMessaging.send(phone,
                "Your account is locked due to suspicious activity.\n" +
                "Please contact the shop owner to unlock.");
            return false;
        }

        // Already verified today
        if (user.isVerifiedToday() && verifiedToday(user)) {
            return true;
        }

        // Needs OTP — trigger verification
        sendOtp(phone, user);
        return false;
    }

    @Transactional
    public void sendOtp(String phone, ShopUser user) {
        try {
            Verification.creator(verifyServiceSid, "whatsapp:" + phone, "whatsapp")
                .create();

            auditRepo.save(AuditLog.of(phone, AuditAction.OTP_REQUESTED,
                "Daily OTP sent", true));

            log.info("OTP sent to {}", phone);
            twilioMessaging.send(phone,
                "Good morning! A verification code has been sent to this number.\n" +
                "Please reply with your code to start billing.");

        } catch (Exception e) {
            log.error("Failed to send OTP to {}: {}", phone, e.getMessage());
            twilioMessaging.send(phone,
                "Could not send verification code. Please try again.");
        }
    }

    @Transactional
    public boolean verifyOtp(String phone, String code) {
        try {
            VerificationCheck check = VerificationCheck
                .creator(verifyServiceSid)
                .setTo("whatsapp:" + phone)
                .setCode(code.trim())
                .create();

            if ("approved".equals(check.getStatus())) {
                // Mark verified for today
                ShopUser user = userRepo.findByPhone(phone).orElseThrow();
                user.setVerifiedToday(true);
                user.setVerifiedAt(LocalDateTime.now());
                userRepo.save(user);

                auditRepo.save(AuditLog.of(phone, AuditAction.OTP_VERIFIED,
                    "Verified at " + LocalDateTime.now(), true));

                log.info("OTP verified for {}", phone);
                twilioMessaging.send(phone,
                    "Verified! Good to go.\n\nTell me what you sold.");
                return true;

            } else {
                auditRepo.save(AuditLog.of(phone, AuditAction.OTP_FAILED,
                    "Invalid code attempt", false));
                twilioMessaging.send(phone,
                    "Incorrect code. Please try again or request a new one.");
                return false;
            }

        } catch (Exception e) {
            log.error("OTP verification error for {}: {}", phone, e.getMessage());
            twilioMessaging.send(phone,
                "Verification failed. Please try again.");
            return false;
        }
    }

    // OTP stays valid for the calendar day it was issued
    private boolean verifiedToday(ShopUser user) {
        return user.getVerifiedAt() != null &&
               user.getVerifiedAt().toLocalDate().equals(LocalDate.now());
    }
}