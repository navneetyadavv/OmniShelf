package com.omnishelf.engine.service;

import com.omnishelf.engine.config.ShopConfig;
import com.omnishelf.engine.dto.*;
import com.omnishelf.engine.model.*;
import com.omnishelf.engine.repository.*;
import com.omnishelf.engine.security.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BillingService {

    @Value("${session.timeout-minutes:20}")
    private int sessionTimeoutMinutes;

    private final BillRepository           billRepo;
    private final BillItemRepository       itemRepo;
    private final BillSessionRepository    sessionRepo;
    private final ProductVariantRepository variantRepo;
    private final AuditLogRepository       auditRepo;
    private final TwilioMessagingService   twilioMessaging;
    private final NlpParserService         nlpParser;
    private final FallbackNlpParserService fallbackParser;
    private final FuzzyMatcherService      fuzzyMatcher;
    private final BillNumberGenerator      billNumberGen;
    private final CustomerService          customerService;
    private final ShopConfig               shopConfig;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Entry point: processes a raw WhatsApp message and adds items to the
     * current draft bill for this shopkeeper.
     */
    @Transactional
    public void processMessage(String shopkeeperPhone, String rawMessage) {
        BillSession session = getOrCreateSession(shopkeeperPhone);

        if (session.isExpired()) {
            twilioMessaging.send(shopkeeperPhone,
                "⏰ Your session timed out after inactivity. Starting fresh.\n" +
                "Send items to begin a new bill.");
            cancelSession(session);
            session = createNewSession(shopkeeperPhone);
        }

        session.refreshExpiry(sessionTimeoutMinutes);

        // Parse via Gemini; fall back to rule-based parser if Gemini is down
        ParsedOrder parsed = nlpParser.parse(rawMessage);
        if (parsed == null) {
            log.warn("Gemini unavailable — using fallback NLP for: {}", rawMessage);
            twilioMessaging.send(shopkeeperPhone,
                "⚠ AI service temporarily unavailable — using basic mode.\n" +
                "Format: 2 Nike Air 42  OR  Samsung 128GB x1");
            parsed = fallbackParser.parse(rawMessage);
        }

        if (parsed.getError() != null) {
            twilioMessaging.send(shopkeeperPhone,
                "I couldn't understand that. Try:\n" +
                "  *2 Nike Air Max size 8 black*\n" +
                "  *Samsung S24 128GB Blue x1*\n\n" +
                "Or type *HELP* for all commands.");
            return;
        }

        Bill bill = getOrCreateDraftBill(session, shopkeeperPhone);

        // Set customer on the bill (first message that has a name wins)
        if (parsed.getCustomerName() != null && bill.getCustomerName() == null) {
            Customer customer = customerService.upsert(parsed.getCustomerName());
            bill.setCustomerName(parsed.getCustomerName());
            bill.setCustomer(customer);
        }

        StringBuilder reply = new StringBuilder();
        boolean anyAdded = false;

        for (ParsedItem parsedItem : parsed.getItems()) {
            MatchResult match = fuzzyMatcher.match(parsedItem);

            switch (match.getStatus()) {
                case FOUND -> {
                    addItemToBill(bill, match.getVariant(), match.getRequestedQuantity());
                    session.setLastUndoItemId(bill.getItems().get(bill.getItems().size() - 1).getId());
                    anyAdded = true;

                    String fuzzyNote = match.isWasFuzzyMatch()
                        ? String.format("\n  _(matched \"%s\" to %s)_",
                            match.getOriginalInput(), match.getSuggestedBrand())
                        : "";

                    reply.append(String.format("✓ Added: %s %s x%d — ₹%.0f%s\n",
                        match.getVariant().getProduct().getBrand(),
                        buildVariantLabel(match.getVariant()),
                        match.getRequestedQuantity(),
                        match.getVariant().getPrice().multiply(BigDecimal.valueOf(match.getRequestedQuantity())),
                        fuzzyNote));
                }
                case INSUFFICIENT_STOCK -> {
                    reply.append(String.format("❌ *%s* — only %d in stock (you asked for %d)\n",
                        parsedItem.getBrand() != null ? parsedItem.getBrand() : parsedItem.getRawBrand(),
                        match.getVariant().getStockQuantity(),
                        match.getRequestedQuantity()));
                }
                case VARIANT_NOT_FOUND -> {
                    reply.append(String.format("❓ *%s* found but variant not matched. Be more specific?\n",
                        parsedItem.getBrand()));
                }
                case NO_MATCH -> {
                    reply.append(String.format("❓ *%s* not found in inventory. Check spelling?\n",
                        parsedItem.getRawBrand() != null ? parsedItem.getRawBrand()
                            : parsedItem.getRawText()));
                }
            }
        }

        if (anyAdded) {
            recalcTotals(bill);
            billRepo.save(bill);
            sessionRepo.save(session);

            reply.append("\n").append(buildBillSummary(bill));
            reply.append("\n\nReply *DONE* to confirm, *UNDO* to remove last item, *CANCEL* to discard.");
        }

        if (reply.length() > 0) {
            twilioMessaging.send(shopkeeperPhone, reply.toString().trim());
        }
    }

    /**
     * Confirms the current draft bill: deducts stock, generates PDF, sends to customer.
     */
    @Transactional
    public Optional<Bill> confirmBill(String shopkeeperPhone) {
        Optional<BillSession> sessionOpt = getActiveSession(shopkeeperPhone);
        if (sessionOpt.isEmpty()) {
            twilioMessaging.send(shopkeeperPhone, "No active bill to confirm.");
            return Optional.empty();
        }

        BillSession session = sessionOpt.get();
        Bill bill = session.getBill();

        if (bill == null || bill.getItems().isEmpty()) {
            twilioMessaging.send(shopkeeperPhone, "Add at least one item before confirming.");
            return Optional.empty();
        }

        // Deduct stock (ACID — inside @Transactional)
        for (BillItem item : bill.getItems()) {
            ProductVariant v = item.getVariant();
            if (v.getStockQuantity() < item.getQuantity()) {
                twilioMessaging.send(shopkeeperPhone,
                    String.format("⚠ Stock for *%s %s* dropped to %d since you added it. Cannot confirm.",
                        v.getProduct().getBrand(), buildVariantLabel(v), v.getStockQuantity()));
                return Optional.empty();
            }
            v.setStockQuantity(v.getStockQuantity() - item.getQuantity());
            variantRepo.save(v);
        }

        bill.setStatus(BillStatus.CONFIRMED);
        bill.setConfirmedAt(LocalDateTime.now());
        bill.setBillNumber(billNumberGen.next());
        bill.setTraceId(TraceContext.get());
        billRepo.save(bill);

        session.setState(SessionState.CONFIRMED);
        sessionRepo.save(session);

        auditRepo.save(AuditLog.of(shopkeeperPhone, AuditAction.BILL_CONFIRMED,
            "Bill: " + bill.getBillNumber() + " | ₹" + bill.getGrandTotal(),
            true, TraceContext.get()));

        log.info("Bill confirmed: {} | ₹{} | trace={}", bill.getBillNumber(), bill.getGrandTotal(), TraceContext.get());
        twilioMessaging.send(shopkeeperPhone,
            String.format("✅ Bill *%s* confirmed!\nTotal: ₹%.2f (incl. GST)\nGenerating PDF...",
                bill.getBillNumber(), bill.getGrandTotal()));

        return Optional.of(bill);
    }

    /**
     * Removes the last added item (undo buffer).
     */
    @Transactional
    public void undoLastItem(String shopkeeperPhone) {
        Optional<BillSession> sessionOpt = getActiveSession(shopkeeperPhone);
        if (sessionOpt.isEmpty()) {
            twilioMessaging.send(shopkeeperPhone, "No active session.");
            return;
        }

        BillSession session = sessionOpt.get();
        Bill bill = session.getBill();

        if (bill == null || bill.getItems().isEmpty()) {
            twilioMessaging.send(shopkeeperPhone, "Nothing to undo.");
            return;
        }

        BillItem last = bill.getItems().remove(bill.getItems().size() - 1);
        itemRepo.delete(last);
        recalcTotals(bill);
        billRepo.save(bill);
        session.refreshExpiry(sessionTimeoutMinutes);
        sessionRepo.save(session);

        twilioMessaging.send(shopkeeperPhone,
            String.format("↩ Removed: %s %s x%d\n\n%s",
                last.getVariant().getProduct().getBrand(),
                buildVariantLabel(last.getVariant()),
                last.getQuantity(),
                bill.getItems().isEmpty()
                    ? "Bill is now empty."
                    : buildBillSummary(bill)));
    }

    /**
     * Cancels the current draft session without deducting stock.
     */
    @Transactional
    public void cancelSession(String shopkeeperPhone) {
        Optional<BillSession> sessionOpt = getActiveSession(shopkeeperPhone);
        if (sessionOpt.isEmpty()) {
            twilioMessaging.send(shopkeeperPhone, "No active session to cancel.");
            return;
        }
        cancelSession(sessionOpt.get());
        twilioMessaging.send(shopkeeperPhone, "🗑 Bill discarded. Send items to start a new one.");
    }

    /**
     * Owner-only: cancel a CONFIRMED bill and restock inventory.
     */
    @Transactional
    public void cancelConfirmedBill(String ownerPhone, String billNumber, String reason) {
        Optional<Bill> billOpt = billRepo.findByBillNumber(billNumber);
        if (billOpt.isEmpty()) {
            twilioMessaging.send(ownerPhone, "Bill *" + billNumber + "* not found.");
            return;
        }

        Bill bill = billOpt.get();
        if (bill.getStatus() != BillStatus.CONFIRMED) {
            twilioMessaging.send(ownerPhone, "Bill is not in CONFIRMED state.");
            return;
        }

        // Restock
        for (BillItem item : bill.getItems()) {
            ProductVariant v = item.getVariant();
            v.setStockQuantity(v.getStockQuantity() + item.getQuantity());
            variantRepo.save(v);
        }

        bill.setStatus(BillStatus.CANCELLED);
        bill.setCancelledAt(LocalDateTime.now());
        bill.setCancelledBy(ownerPhone);
        billRepo.save(bill);

        auditRepo.save(AuditLog.of(ownerPhone, AuditAction.BILL_CANCELLED,
            "Cancelled: " + billNumber + " | Reason: " + reason, true, TraceContext.get()));

        log.info("Bill {} cancelled by {} — reason: {}", billNumber, ownerPhone, reason);
        twilioMessaging.send(ownerPhone,
            "✅ Bill *" + billNumber + "* cancelled and stock restocked.");
    }

    /**
     * Reprints (resends) PDF for a confirmed bill.
     */
    @Transactional
    public Optional<Bill> getBillForReprint(String phone, String billNumber) {
        Optional<Bill> billOpt = billRepo.findByBillNumber(billNumber);
        if (billOpt.isEmpty()) {
            twilioMessaging.send(phone, "Bill *" + billNumber + "* not found.");
            return Optional.empty();
        }
        Bill bill = billOpt.get();
        if (bill.getStatus() != BillStatus.CONFIRMED) {
            twilioMessaging.send(phone, "Only confirmed bills can be reprinted.");
            return Optional.empty();
        }
        auditRepo.save(AuditLog.of(phone, AuditAction.BILL_REPRINT,
            "Reprint: " + billNumber, true, TraceContext.get()));
        return billOpt;
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private void addItemToBill(Bill bill, ProductVariant variant, int qty) {
        BillItem item = new BillItem();
        item.setBill(bill);
        item.setVariant(variant);
        item.setQuantity(qty);
        item.setUnitPrice(variant.getPrice());
        item.setLineTotal(variant.getPrice().multiply(BigDecimal.valueOf(qty)));

        BigDecimal gstRate = variant.getGstRatePercent()
            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        item.setGstAmount(item.getLineTotal().multiply(gstRate).setScale(2, RoundingMode.HALF_UP));

        bill.getItems().add(item);
    }

    /**
     * Recalculates subtotal, CGST/SGST breakdown, and grand total.
     * Prices are assumed to be exclusive of GST.
     */
    private void recalcTotals(Bill bill) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalGst = BigDecimal.ZERO;

        for (BillItem item : bill.getItems()) {
            subtotal = subtotal.add(item.getLineTotal());
            totalGst = totalGst.add(item.getGstAmount());
        }

        bill.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        bill.setTaxableAmount(subtotal.setScale(2, RoundingMode.HALF_UP));

        // For intra-state: CGST = SGST = half of total GST
        BigDecimal half = totalGst.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        bill.setCgst(half);
        bill.setSgst(half);
        bill.setTaxAmount(totalGst.setScale(2, RoundingMode.HALF_UP));

        BigDecimal grandTotal = subtotal.add(totalGst).subtract(bill.getDiscountAmount());
        bill.setGrandTotal(grandTotal.setScale(2, RoundingMode.HALF_UP));
        bill.setTotalAmount(bill.getGrandTotal());
    }

    private BillSession getOrCreateSession(String phone) {
        List<SessionState> activeStates = List.of(
            SessionState.ACTIVE, SessionState.AWAITING_CONFIRMATION);
        return sessionRepo
            .findByShopkeeperPhoneAndStateIn(phone, activeStates)
            .orElseGet(() -> createNewSession(phone));
    }

    private Optional<BillSession> getActiveSession(String phone) {
        return sessionRepo.findByShopkeeperPhoneAndStateIn(phone,
            List.of(SessionState.ACTIVE, SessionState.AWAITING_CONFIRMATION));
    }

    private BillSession createNewSession(String phone) {
        BillSession s = new BillSession();
        s.setShopkeeperPhone(phone);
        s.setState(SessionState.ACTIVE);
        s.setExpiresAt(LocalDateTime.now().plusMinutes(sessionTimeoutMinutes));
        return sessionRepo.save(s);
    }

    private Bill getOrCreateDraftBill(BillSession session, String phone) {
        if (session.getBill() != null) return session.getBill();

        Bill bill = new Bill();
        bill.setShopkeeperPhone(phone);
        bill.setStatus(BillStatus.DRAFT);
        bill.setDiscountAmount(BigDecimal.ZERO);
        bill.setTraceId(TraceContext.get());
        Bill saved = billRepo.save(bill);

        session.setBill(saved);
        sessionRepo.save(session);
        return saved;
    }

    private void cancelSession(BillSession session) {
        session.setState(SessionState.CANCELLED);
        if (session.getBill() != null) {
            session.getBill().setStatus(BillStatus.CANCELLED);
            billRepo.save(session.getBill());
        }
        sessionRepo.save(session);
    }

    private String buildBillSummary(Bill bill) {
        StringBuilder sb = new StringBuilder("📋 *Current Bill*\n");
        for (BillItem item : bill.getItems()) {
            sb.append(String.format("  %s %s x%d = ₹%.0f\n",
                item.getVariant().getProduct().getBrand(),
                buildVariantLabel(item.getVariant()),
                item.getQuantity(),
                item.getLineTotal()));
        }
        sb.append(String.format("\nSubtotal: ₹%.2f", bill.getSubtotal()));
        sb.append(String.format("\nGST (CGST+SGST): ₹%.2f", bill.getTaxAmount()));
        if (bill.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("\nDiscount: -₹%.2f", bill.getDiscountAmount()));
        }
        sb.append(String.format("\n*Grand Total: ₹%.2f*", bill.getGrandTotal()));
        return sb.toString();
    }

    private String buildVariantLabel(ProductVariant v) {
        StringBuilder sb = new StringBuilder();
        if (v.getColor()   != null) sb.append(v.getColor()).append(" ");
        if (v.getSize()    != null) sb.append("Sz").append(v.getSize()).append(" ");
        if (v.getStorage() != null) sb.append(v.getStorage());
        return sb.toString().trim();
    }
}
