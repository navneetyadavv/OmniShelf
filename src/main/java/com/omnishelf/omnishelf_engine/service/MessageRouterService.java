package com.billing.service;

import com.billing.model.AuditAction;
import com.billing.model.AuditLog;
import com.billing.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class MessageRouterService {

    private final TwilioMessagingService   twilioMessaging;
    private final NlpOrchestrationService  nlpOrchestration;
    private final SessionManagerService    sessionManager;
    private final OtpService               otpService;
    private final RbacService              rbacService;
    private final RateLimiterService       rateLimiter;
    private final AuditLogRepository       auditRepo;

    public MessageRouterService(TwilioMessagingService twilioMessaging,
                                 NlpOrchestrationService nlpOrchestration,
                                 SessionManagerService sessionManager,
                                 OtpService otpService,
                                 RbacService rbacService,
                                 RateLimiterService rateLimiter,
                                 AuditLogRepository auditRepo) {
        this.twilioMessaging  = twilioMessaging;
        this.nlpOrchestration = nlpOrchestration;
        this.sessionManager   = sessionManager;
        this.otpService       = otpService;
        this.rbacService      = rbacService;
        this.rateLimiter      = rateLimiter;
        this.auditRepo        = auditRepo;
    }

    public void route(String phone, String text) {
        String lower = text.toLowerCase().trim();
        log.info("Message from {} | text: '{}'", phone, lower);

        // ── Layer 1: OTP verification gate ───────────────────────────
        // OTP reply is the only message allowed through unauthenticated
        if (isOtpReply(lower)) {
            otpService.verifyOtp(phone, text.trim());
            return;
        }

        if (!otpService.isAuthorised(phone)) {
            // isAuthorised() already sends the OTP or rejection message
            return;
        }

        // ── Layer 2: Owner-only unlock command ────────────────────────
        if (lower.startsWith("unlock ")) {
            String targetPhone = text.substring(7).trim();
            rateLimiter.unlockAccount(phone, targetPhone);
            return;
        }

        // ── Layer 3: Regular command routing ──────────────────────────
        if (isGreeting(lower)) {
            handleGreeting(phone);

        } else if (lower.equals("help") || lower.equals("?")) {
            handleHelp(phone);

        } else if (lower.equals("done") || lower.equals("confirm")
                || lower.equals("ho gaya") || lower.equals("bas")) {
            // Rate limit check before confirmation
            if (rateLimiter.isAllowed(phone)) {
                sessionManager.requestConfirmation(phone);
            }

        } else if (lower.equals("yes") || lower.equals("haan")
                || lower.equals("ha") || lower.equals("ok")) {
            sessionManager.handleYesReply(phone);

        } else if (lower.equals("no") || lower.equals("nahi")
                || lower.equals("nope")) {
            sessionManager.handleNoReply(phone);

        } else if (lower.equals("undo") || lower.equals("hatao")
                || lower.equals("remove last")) {
            sessionManager.undoLastItem(phone);

        } else if (lower.equals("cancel") || lower.equals("start over")
                || lower.equals("reset")) {
            sessionManager.cancelSession(phone);

        } else if (lower.startsWith("discount ") && lower.contains("₹")) {
            // Discount command — RBAC check
            handleDiscountCommand(phone, text);

        } else {
            // Everything else → NLP pipeline
            auditRepo.save(AuditLog.of(phone,
                AuditAction.BILL_CREATED, text, true));
            nlpOrchestration.processOrderMessage(phone, text);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void handleDiscountCommand(String phone, String text) {
        // Parse "discount ₹500" or "discount 10%"
        try {
            String amountStr = text.replaceAll("[^0-9.]", "");
            java.math.BigDecimal amount = new java.math.BigDecimal(amountStr);

            if (rbacService.canApplyDiscount(phone, amount)) {
                sessionManager.applyDiscount(phone, amount);
            }
            // canApplyDiscount sends the rejection message if blocked

        } catch (NumberFormatException e) {
            twilioMessaging.send(phone,
                "Invalid discount format. Try: *discount ₹500*");
        }
    }

    private boolean isOtpReply(String lower) {
        // OTP codes are 6-digit numbers
        return lower.matches("\\d{6}");
    }

    private boolean isGreeting(String lower) {
        return lower.matches(
            "(hi|hello|hey|start|hii|helo|namaste|namaskar)([.!\\s].*)?");
    }

    private void handleGreeting(String phone) {
        boolean isOwner = rbacService.isOwner(phone);
        twilioMessaging.send(phone,
            "Welcome back!\n\n" +
            "Tell me what you sold and I'll handle the rest.\n\n" +
            "Examples:\n" +
            "  2 Nike size 8 black\n" +
            "  ek samsung s24 128gb\n\n" +
            "Commands: done | undo | cancel | help" +
            (isOwner ? "\n\nOwner: discount ₹500 | unlock +91XXXXXXXXXX" : "")
        );
    }

    private void handleHelp(String phone) {
        boolean isOwner = rbacService.isOwner(phone);
        StringBuilder help = new StringBuilder();
        help.append("How to use:\n\n");
        help.append("1. Tell me what you sold\n");
        help.append("2. Say *done* to review\n");
        help.append("3. Say *YES* to confirm & get PDF\n\n");
        help.append("Commands:\n");
        help.append("  done — confirm bill\n");
        help.append("  undo — remove last item\n");
        help.append("  cancel — clear cart\n");
        if (isOwner) {
            help.append("\nOwner commands:\n");
            help.append("  discount ₹500 — apply discount\n");
            help.append("  unlock +91XXXXXXXXXX — unlock staff\n");
        }
        twilioMessaging.send(phone, help.toString());
    }
}