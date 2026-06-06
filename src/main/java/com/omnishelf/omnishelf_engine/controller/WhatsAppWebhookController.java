package com.omnishelf.engine.controller;

import com.omnishelf.engine.model.Bill;
import com.omnishelf.engine.security.TraceContext;
import com.omnishelf.engine.security.TwilioSignatureValidator;
import com.omnishelf.engine.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/webhook")
@Slf4j
@RequiredArgsConstructor
public class WhatsAppWebhookController {

    private final OtpService                 otpService;
    private final BillingService             billingService;
    private final RateLimiterService         rateLimiterService;
    private final PdfDeliveryService         pdfDeliveryService;
    private final TwilioMessagingService     twilioMessaging;
    private final TwilioSignatureValidator   signatureValidator;

    /**
     * Twilio calls this endpoint for every incoming WhatsApp message.
     *
     * Flow:
     * 1. Verify HMAC-SHA1 signature (reject forgeries)
     * 2. Init trace ID for structured logging
     * 3. Check if user is registered + not locked
     * 4. Check rate limits
     * 5. Route the message to the appropriate handler
     * 6. Return 200 immediately — actual work is done asynchronously
     */
    @PostMapping(value = "/whatsapp", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> handleIncomingMessage(
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {

        // ── 1. HMAC-SHA1 Webhook Signature Verification ────────────────────────
        String signature  = request.getHeader("X-Twilio-Signature");
        String requestUrl = request.getRequestURL().toString();

        if (!signatureValidator.isValid(signature, requestUrl, params)) {
            log.warn("Rejected unsigned/invalid webhook request from {}", request.getRemoteAddr());
            return ResponseEntity.status(403).body("Forbidden");
        }

        // ── 2. Init correlation trace ID ───────────────────────────────────────
        String traceId = TraceContext.init();

        try {
            String from = params.get("From");
            String body = params.getOrDefault("Body", "").trim();

            if (from == null || from.isBlank()) {
                log.warn("[{}] Received webhook with missing 'From' field", traceId);
                return ResponseEntity.ok("OK");
            }

            // Strip "whatsapp:" prefix
            String phone = from.startsWith("whatsapp:") ? from.substring(9) : from;
            log.info("[{}] Incoming from={} body={}", traceId, phone, body);

            // ── 3. OTP check — is this user verified today? ──────────────────
            if (isOtpResponse(body)) {
                otpService.verifyOtp(phone, body.trim());
                return ResponseEntity.ok("OK");
            }

            if (!otpService.isAuthorised(phone)) {
                // isAuthorised() already sends the OTP or "not registered" message
                return ResponseEntity.ok("OK");
            }

            // ── 4. Rate limiting ─────────────────────────────────────────────
            if (!rateLimiterService.isAllowed(phone)) {
                return ResponseEntity.ok("OK");
            }

            // ── 5. Route command ─────────────────────────────────────────────
            routeMessage(phone, body);

        } finally {
            TraceContext.clear();
        }

        return ResponseEntity.ok("OK");
    }

    // ── Command routing ────────────────────────────────────────────────────────

    private void routeMessage(String phone, String body) {
        String upper = body.toUpperCase().trim();

        if (upper.equals("DONE") || upper.equals("CONFIRM") || upper.equals("YES")) {
            billingService.confirmBill(phone)
                .ifPresent(bill -> pdfDeliveryService.generateAndSend(bill, phone));
            return;
        }

        if (upper.equals("UNDO")) {
            billingService.undoLastItem(phone);
            return;
        }

        if (upper.equals("CANCEL") || upper.equals("DISCARD")) {
            billingService.cancelSession(phone);
            return;
        }

        if (upper.startsWith("REPRINT ")) {
            String billNo = body.substring(8).trim();
            Optional<Bill> bill = billingService.getBillForReprint(phone, billNo);
            bill.ifPresent(b -> pdfDeliveryService.generateAndSend(b, phone));
            return;
        }

        if (upper.startsWith("CANCEL BILL ")) {
            String[] parts = body.split(" ", 4);
            String billNo  = parts.length >= 3 ? parts[2] : "";
            String reason  = parts.length >= 4 ? parts[3] : "Owner requested";
            billingService.cancelConfirmedBill(phone, billNo, reason);
            return;
        }

        if (upper.startsWith("UNLOCK ")) {
            String targetPhone = body.substring(7).trim();
            rateLimiterService.unlockAccount(phone, targetPhone);
            return;
        }

        if (upper.equals("HELP") || upper.equals("?")) {
            sendHelp(phone);
            return;
        }

        if (upper.equals("STATUS") || upper.equals("STOCK")) {
            // Handled by /status endpoint but also via WhatsApp
            twilioMessaging.send(phone, "Visit the dashboard for stock and sales reports.");
            return;
        }

        // Default: treat as a billing message
        billingService.processMessage(phone, body);
    }

    private boolean isOtpResponse(String body) {
        // A 4-8 digit string = OTP response
        return body.trim().matches("\\d{4,8}");
    }

    private void sendHelp(String phone) {
        twilioMessaging.send(phone,
            "*OmniShelf — Command Reference*\n\n" +
            "*Add items:*\n" +
            "  2 Nike Air Max size 8 Black\n" +
            "  Samsung S24 128GB Blue x1\n" +
            "  ek Niki ka juta size saat (Hindi/Hinglish OK)\n\n" +
            "*Session commands:*\n" +
            "  DONE or YES — finalize & generate invoice\n" +
            "  UNDO — remove last added item\n" +
            "  CANCEL — discard current bill\n\n" +
            "*Invoice commands:*\n" +
            "  REPRINT OMNI-20240615-0001 — resend PDF\n" +
            "  CANCEL BILL OMNI-... reason — cancel a confirmed bill (Owner only)\n\n" +
            "*Admin commands (Owner only):*\n" +
            "  UNLOCK +91XXXXXXXXXX — unlock a locked account\n\n" +
            "Reply with a product description to start billing.");
    }
}
