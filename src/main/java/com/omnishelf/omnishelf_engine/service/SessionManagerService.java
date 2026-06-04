package com.omnishelf.omnishelf_engine.service;

import com.omnishelf.omnishelf_engine.model.*;
import com.omnishelf.omnishelf_engine.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class SessionManagerService {

    private static final BigDecimal GST_RATE = new BigDecimal("0.18"); // 18% GST

    private final BillSessionRepository sessionRepo;
    private final BillRepository        billRepo;
    private final BillItemRepository    billItemRepo;
    private final ProductVariantRepository variantRepo;
    private final TwilioMessagingService twilioMessaging;
    private final InvoiceDeliveryService invoiceDelivery;

    public SessionManagerService(BillSessionRepository sessionRepo,
                                  BillRepository billRepo,
                                  BillItemRepository billItemRepo,
                                  ProductVariantRepository variantRepo,
                                  TwilioMessagingService twilioMessaging,
                                  InvoiceDeliveryService invoiceDelivery) {
        this.sessionRepo     = sessionRepo;
        this.billRepo        = billRepo;
        this.billItemRepo    = billItemRepo;
        this.variantRepo     = variantRepo;
        this.twilioMessaging = twilioMessaging;
        this.invoiceDelivery = invoiceDelivery;
    }

    // ── Session retrieval ─────────────────────────────────────────────

    /**
     * Gets the current ACTIVE or AWAITING session for a phone.
     * Returns empty if no session exists or it has expired.
     */
    public Optional<BillSession> getActiveSession(String phone) {
        Optional<BillSession> session = sessionRepo
            .findByShopkeeperPhoneAndStateIn(phone,
                List.of(SessionState.ACTIVE, SessionState.AWAITING_CONFIRMATION));

        if (session.isPresent() && session.get().isExpired()) {
            log.info("Session expired for phone: {}", phone);
            expireSession(session.get());
            return Optional.empty();
        }

        return session;
    }

    // ── Add item to session ───────────────────────────────────────────

    /**
     * Adds a matched variant to the session's draft bill.
     * Creates a new session + bill if none exists.
     * ACID: stock is NOT deducted here — only on final confirmation.
     */
    @Transactional
    public void addItemToSession(String phone, ProductVariant variant,
                                  int quantity, String customerName) {

        BillSession session = getOrCreateSession(phone);
        Bill bill = session.getBill();

        // Update customer name if provided
        if (customerName != null && !customerName.isBlank()) {
            bill.setCustomerName(customerName);
        }

        // Check if this variant is already in the cart — merge qty if so
        Optional<BillItem> existingItem = bill.getItems().stream()
            .filter(i -> i.getVariant().getId().equals(variant.getId()))
            .findFirst();

        if (existingItem.isPresent()) {
            BillItem item = existingItem.get();
            int newQty = item.getQuantity() + quantity;

            // Re-check stock for merged quantity
            if (variant.getStockQuantity() < newQty) {
                twilioMessaging.send(phone,
                    String.format("Only %d units of %s available. Cart has %d already.",
                        variant.getStockQuantity(), variant.getSku(), item.getQuantity()));
                return;
            }

            item.setQuantity(newQty);
            item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(newQty)));
            billItemRepo.save(item);
            session.setLastUndoItemId(item.getId());
            log.info("Merged qty for SKU {} → new qty: {}", variant.getSku(), newQty);

        } else {
            // Brand new line item
            BillItem newItem = new BillItem();
            newItem.setBill(bill);
            newItem.setVariant(variant);
            newItem.setQuantity(quantity);
            newItem.setUnitPrice(variant.getPrice());
            newItem.setLineTotal(variant.getPrice().multiply(BigDecimal.valueOf(quantity)));
            billItemRepo.save(newItem);
            session.setLastUndoItemId(newItem.getId());
            bill.getItems().add(newItem);
            log.info("Added new item to bill: {} x{}", variant.getSku(), quantity);
        }

        recalculateBillTotals(bill);
        session.refreshExpiry();
        sessionRepo.save(session);
        billRepo.save(bill);

        sendCartSummary(phone, bill, "Item added!");
    }

    // ── Undo last item ────────────────────────────────────────────────

    @Transactional
    public void undoLastItem(String phone) {
        Optional<BillSession> sessionOpt = getActiveSession(phone);

        if (sessionOpt.isEmpty()) {
            twilioMessaging.send(phone, "No active cart to undo.");
            return;
        }

        BillSession session = sessionOpt.get();
        String undoItemId = session.getLastUndoItemId();

        if (undoItemId == null) {
            twilioMessaging.send(phone, "Nothing to undo.");
            return;
        }

        Optional<BillItem> itemOpt = billItemRepo.findById(undoItemId);
        if (itemOpt.isEmpty()) {
            twilioMessaging.send(phone, "Nothing to undo.");
            return;
        }

        BillItem item = itemOpt.get();
        Bill bill = session.getBill();
        String removedSku = item.getVariant().getSku();
        int removedQty = item.getQuantity();

        bill.getItems().remove(item);
        billItemRepo.delete(item);

        session.setLastUndoItemId(null); // one-level undo only
        recalculateBillTotals(session.getBill());
        session.refreshExpiry();
        sessionRepo.save(session);
        billRepo.save(session.getBill());

        log.info("Undo: removed {} x{} from bill for {}", removedSku, removedQty, phone);

        if (session.getBill().getItems().isEmpty()) {
            twilioMessaging.send(phone,
                "Removed: " + removedSku + " x" + removedQty + "\n\nCart is now empty.");
        } else {
            sendCartSummary(phone, session.getBill(),
                "Removed: " + removedSku + " x" + removedQty);
        }
    }

    // ── Confirm bill (move to AWAITING) ───────────────────────────────

    @Transactional
    public void requestConfirmation(String phone) {
        Optional<BillSession> sessionOpt = getActiveSession(phone);

        if (sessionOpt.isEmpty()) {
            twilioMessaging.send(phone,
                "No active cart found. Start by telling me what you sold.");
            return;
        }

        BillSession session = sessionOpt.get();
        Bill bill = session.getBill();

        if (bill.getItems().isEmpty()) {
            twilioMessaging.send(phone, "Cart is empty. Add items first.");
            return;
        }

        session.setState(SessionState.AWAITING_CONFIRMATION);
        session.refreshExpiry();
        sessionRepo.save(session);

        // Show final summary for review
        StringBuilder sb = new StringBuilder();
        sb.append("*Review your bill:*\n\n");
        for (BillItem item : bill.getItems()) {
            sb.append(String.format("%dx %s — ₹%.0f\n",
                item.getQuantity(), item.getVariant().getSku(), item.getLineTotal()));
        }
        sb.append(String.format("\nSubtotal: ₹%.0f", bill.getTotalAmount()));
        sb.append(String.format("\nGST (18%%): ₹%.0f", bill.getTaxAmount()));
        sb.append(String.format("\n*Grand total: ₹%.0f*", bill.getGrandTotal()));

        if (bill.getCustomerName() != null) {
            sb.append("\nCustomer: ").append(bill.getCustomerName());
        }

        sb.append("\n\nReply *YES* to confirm & generate invoice");
        sb.append("\nReply *NO* to keep editing");

        twilioMessaging.send(phone, sb.toString());
    }

    // ── Final confirmation + stock deduction (ACID) ───────────────────

    @Transactional
    public void confirmBill(String phone) {
        Optional<BillSession> sessionOpt = sessionRepo
            .findByShopkeeperPhoneAndStateIn(phone,
                List.of(SessionState.AWAITING_CONFIRMATION));

        if (sessionOpt.isEmpty()) {
            twilioMessaging.send(phone, "Nothing to confirm. Say *done* first.");
            return;
        }

        BillSession session = sessionOpt.get();
        Bill bill = session.getBill();

        // ACID stock deduction — all items or none
        for (BillItem item : bill.getItems()) {
            ProductVariant variant = item.getVariant();

            // Re-check stock at confirmation time (may have changed since adding to cart)
            if (variant.getStockQuantity() < item.getQuantity()) {
                twilioMessaging.send(phone,
                    String.format(
                        "Stock changed! Only %d units of %s available now.\n" +
                        "Please edit your cart and try again.",
                        variant.getStockQuantity(), variant.getSku()));
                // Roll back to ACTIVE so they can fix it
                session.setState(SessionState.ACTIVE);
                sessionRepo.save(session);
                return;
            }

            // Deduct stock atomically
            variant.setStockQuantity(variant.getStockQuantity() - item.getQuantity());
            variantRepo.save(variant);
            log.info("Stock deducted: {} → {} remaining", variant.getSku(),
                variant.getStockQuantity());
        }

        // Finalize bill
        bill.setStatus(BillStatus.CONFIRMED);
        bill.setConfirmedAt(LocalDateTime.now());
        bill.setBillNumber(generateBillNumber());
        billRepo.save(bill);

        session.setState(SessionState.CONFIRMED);
        sessionRepo.save(session);

        log.info("Bill confirmed: {} for {}", bill.getBillNumber(), phone);

        twilioMessaging.send(phone,
            String.format(
                "Bill *%s* confirmed!\n\nGenerating PDF invoice...",
                bill.getBillNumber()));

        session.setState(SessionState.CONFIRMED);
        sessionRepo.save(session);

        log.info("Bill confirmed: {} for {}", bill.getBillNumber(), phone);

        // Deliver invoice — Phase 4 fully live
        invoiceDelivery.deliverInvoice(bill, phone);
    }

    // ── Cancel session ────────────────────────────────────────────────

    @Transactional
    public void cancelSession(String phone) {
        Optional<BillSession> sessionOpt = getActiveSession(phone);

        if (sessionOpt.isEmpty()) {
            twilioMessaging.send(phone, "No active session to cancel.");
            return;
        }

        BillSession session = sessionOpt.get();
        Bill bill = session.getBill();

        bill.setStatus(BillStatus.CANCELLED);
        billRepo.save(bill);

        session.setState(SessionState.CANCELLED);
        sessionRepo.save(session);

        log.info("Session cancelled for {}", phone);
        twilioMessaging.send(phone,
            "Cart cleared. Start fresh by telling me what you sold.");
    }

    // ── Handle YES/NO replies (disambiguation + confirmation) ─────────

    public void handleYesReply(String phone) {
        Optional<BillSession> sessionOpt = sessionRepo
            .findByShopkeeperPhoneAndStateIn(phone,
                List.of(SessionState.AWAITING_CONFIRMATION));

        if (sessionOpt.isPresent()) {
            confirmBill(phone);
        } else {
            // YES may also be answering a "Did you mean X?" disambiguation
            // Phase 2 disambiguation handler picks this up — no action here
            twilioMessaging.send(phone, "Nothing to confirm right now.");
        }
    }

    public void handleNoReply(String phone) {
        Optional<BillSession> sessionOpt = sessionRepo
            .findByShopkeeperPhoneAndStateIn(phone,
                List.of(SessionState.AWAITING_CONFIRMATION));

        if (sessionOpt.isPresent()) {
            BillSession session = sessionOpt.get();
            session.setState(SessionState.ACTIVE);
            session.refreshExpiry();
            sessionRepo.save(session);
            sendCartSummary(phone, session.getBill(), "Editing resumed.");
        } else {
            twilioMessaging.send(phone, "No pending confirmation.");
        }
    }

    // ── Scheduled cleanup ─────────────────────────────────────────────

    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    @Transactional
    public void cleanupExpiredSessions() {
        List<BillSession> expired = sessionRepo
            .findExpiredSessions(LocalDateTime.now());

        for (BillSession session : expired) {
            expireSession(session);
        }

        if (!expired.isEmpty()) {
            log.info("Expired {} idle session(s)", expired.size());
        }

        // Hard-delete old closed sessions
        sessionRepo.deleteOldClosedSessions(
            LocalDateTime.now().minusHours(24));
    }

    // ── Private helpers ───────────────────────────────────────────────

    private BillSession getOrCreateSession(String phone) {
        return getActiveSession(phone).orElseGet(() -> {
            Bill bill = new Bill();
            bill.setShopkeeperPhone(phone);
            bill.setStatus(BillStatus.DRAFT);
            billRepo.save(bill);

            BillSession session = new BillSession();
            session.setShopkeeperPhone(phone);
            session.setState(SessionState.ACTIVE);
            session.setBill(bill);
            sessionRepo.save(session);

            log.info("New session created for {}", phone);
            return session;
        });
    }

    private void recalculateBillTotals(Bill bill) {
        BigDecimal subtotal = bill.getItems().stream()
            .map(BillItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tax   = subtotal.multiply(GST_RATE)
                                   .setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax);

        bill.setTotalAmount(subtotal);
        bill.setTaxAmount(tax);
        bill.setGrandTotal(total);
    }

    private void sendCartSummary(String phone, Bill bill, String header) {
        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n\n*Current cart:*\n");

        for (BillItem item : bill.getItems()) {
            sb.append(String.format("  %dx %s — ₹%.0f\n",
                item.getQuantity(),
                item.getVariant().getSku(),
                item.getLineTotal()));
        }

        sb.append(String.format("\nTotal (incl. GST): *₹%.0f*", bill.getGrandTotal()));
        sb.append("\n\nAdd more items or say *done* to confirm.");
        twilioMessaging.send(phone, sb.toString());
    }

    private void expireSession(BillSession session) {
        session.getBill().setStatus(BillStatus.CANCELLED);
        billRepo.save(session.getBill());
        session.setState(SessionState.CANCELLED);
        sessionRepo.save(session);
        twilioMessaging.send(session.getShopkeeperPhone(),
            "Your cart expired after 30 minutes of inactivity. " +
            "Start a new bill by telling me what you sold.");
    }

    private String generateBillNumber() {
        String date = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = billRepo.countByStatus(BillStatus.CONFIRMED) + 1;
        return String.format("BILL-%s-%03d", date, count);
    }
}