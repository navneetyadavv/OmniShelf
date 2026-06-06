package com.omnishelf.engine.service;

import com.omnishelf.engine.model.*;
import com.omnishelf.engine.repository.*;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OtpService {

    @Value("${twilio.verify-service-sid}")
    private String verifyServiceSid;

    @Value("${session.otp-brute-force-limit:5}")
    private int bruteForceLimit;

    @Value("${session.otp-lockout-minutes:30}")
    private int lockoutMinutes;

    private final ShopUserRepository     userRepo;
    private final AuditLogRepository     auditRepo;
    private final TwilioMessagingService twilioMessaging;

    public boolean isAuthorised(String phone) {
        Optional<ShopUser> userOpt = userRepo.findByPhone(phone);

        if (userOpt.isEmpty()) {
            log.warn("Unregistered number attempted access: {}", phone);
            twilioMessaging.send(phone,
                "This number is not registered in the billing system.\n" +
                "Please contact the shop owner.");
            return false;
        }

        ShopUser user = userOpt.get();

        if (user.isLocked()) {
            twilioMessaging.send(phone,
                "Your account is locked due to suspicious activity.\n" +
                "Contact the shop owner to unlock.");
            return false;
        }

        if (user.isOtpBruteForced()) {
            long minutesLeft = java.time.Duration.between(
                LocalDateTime.now(), user.getOtpLockedUntil()).toMinutes() + 1;
            twilioMessaging.send(phone,
                String.format("Too many incorrect codes. Try again in %d minute(s).", minutesLeft));
            return false;
        }

        if (user.isVerifiedToday()) {
            return true;
        }

        sendOtp(phone, user);
        return false;
    }

    @Transactional
    public void sendOtp(String phone, ShopUser user) {
        try {
            Verification.creator(verifyServiceSid, "whatsapp:" + phone, "whatsapp").create();
            auditRepo.save(AuditLog.of(phone, AuditAction.OTP_REQUESTED, "Daily OTP sent", true));
            log.info("OTP sent to {}", phone);
            twilioMessaging.send(phone,
                "Good morning! A verification code has been sent.\n" +
                "Reply with your 6-digit code to start billing.");
        } catch (Exception e) {
            log.error("Failed to send OTP to {}: {}", phone, e.getMessage());
            twilioMessaging.send(phone, "Could not send verification code. Please try again.");
        }
    }

    @Transactional
    public boolean verifyOtp(String phone, String code) {
        Optional<ShopUser> userOpt = userRepo.findByPhone(phone);
        if (userOpt.isEmpty()) {
            twilioMessaging.send(phone, "Number not registered.");
            return false;
        }

        ShopUser user = userOpt.get();

        if (user.isOtpBruteForced()) {
            long minutesLeft = java.time.Duration.between(
                LocalDateTime.now(), user.getOtpLockedUntil()).toMinutes() + 1;
            twilioMessaging.send(phone,
                String.format("Account temporarily locked. Try again in %d minute(s).", minutesLeft));
            return false;
        }

        try {
            VerificationCheck check = VerificationCheck
                .creator(verifyServiceSid)
                .setTo("whatsapp:" + phone)
                .setCode(code.trim())
                .create();

            if ("approved".equals(check.getStatus())) {
                user.setVerifiedToday(true);
                user.setVerifiedAt(LocalDateTime.now());
                user.resetOtpFailures();
                userRepo.save(user);

                auditRepo.save(AuditLog.of(phone, AuditAction.OTP_VERIFIED,
                    "Verified at " + LocalDateTime.now(), true));
                log.info("OTP verified for {}", phone);
                twilioMessaging.send(phone, "✓ Verified! You're good to go.\n\nTell me what you sold.");
                return true;

            } else {
                user.incrementFailedOtp(bruteForceLimit, lockoutMinutes);
                userRepo.save(user);

                int remaining = bruteForceLimit - user.getFailedOtpAttempts();
                auditRepo.save(AuditLog.of(phone, AuditAction.OTP_FAILED, "Wrong code", false));

                if (user.isOtpBruteForced()) {
                    twilioMessaging.send(phone,
                        String.format("Too many incorrect attempts. Locked for %d minutes.", lockoutMinutes));
                } else {
                    twilioMessaging.send(phone,
                        String.format("Incorrect code. %d attempt(s) remaining.", remaining));
                }
                return false;
            }

        } catch (Exception e) {
            log.error("OTP verification error for {}: {}", phone, e.getMessage());
            twilioMessaging.send(phone, "Verification failed. Please try again.");
            return false;
        }
    }
}
