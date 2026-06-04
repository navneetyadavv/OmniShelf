package com.billing.service;

import com.billing.model.*;
import com.billing.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class ReturnService {

    private final BillRepository           billRepo;
    private final BillItemRepository       billItemRepo;
    private final ProductVariantRepository variantRepo;
    private final AuditLogRepository       auditRepo;
    private final InvoiceDeliveryService   invoiceDelivery;
    private final TwilioMessagingService   twilioMessaging;

    public ReturnService(BillRepository billRepo,
                         BillItemRepository billItemRepo,
                         ProductVariantRepository variantRepo,
                         AuditLogRepository auditRepo,
                         InvoiceDeliveryService invoiceDelivery,
                         TwilioMessagingService twilioMessaging) {
        this.billRepo        = billRepo;
        this.billItemRepo    = billItemRepo;
        this.variantRepo     = variantRepo;
        this.auditRepo       = auditRepo;
        this.invoiceDelivery = invoiceDelivery;
        this.twilioMessaging = twilioMessaging;
    }

    /**
     * Cancels a confirmed bill and atomically re-credits all stock.
     * Only CONFIRMED bills can be cancelled — DRAFT bills use
     * SessionManagerService.cancelSession() instead.
     */
    @Transactional
    public void cancelBill(String phone, String billNumber) {
        Optional<Bill> billOpt = billRepo.findByBillNumber(billNumber);

        // Bill not found
        if (billOpt.isEmpty()) {
            twilioMessaging.send(phone,
                "Bill *" + billNumber + "* not found.\n" +
                "Please check the bill number and try again.");
            return;
        }

        Bill bill = billOpt.get();

        // Security: only the shopkeeper who created the bill can cancel it
        if (!bill.getShopkeeperPhone().equals(phone)) {
            twilioMessaging.send(phone,
                "You can only cancel bills you created.");
            auditRepo.save(AuditLog.blocked(phone,
                AuditAction.BILL_CANCELLED,
                "Unauthorised cancel attempt for " + billNumber));
            return;
        }

        // Only confirmed bills can be cancelled via this flow
        if (bill.getStatus() != BillStatus.CONFIRMED) {
            twilioMessaging.send(phone,
                "Bill *" + billNumber + "* cannot be cancelled.\n" +
                "Status: " + bill.getStatus());
            return;
        }

        // Check 24-hour cancellation window
        if (bill.getConfirmedAt().isBefore(LocalDateTime.now().minusHours(24))) {
            twilioMessaging.send(phone,
                "Bill *" + billNumber + "* cannot be cancelled after 24 hours.\n" +
                "Please contact the shop owner.");
            return;
        }

        // ACID stock re-credit — all items or none
        for (BillItem item : bill.getItems()) {
            ProductVariant variant = item.getVariant();
            int restoredQty = variant.getStockQuantity() + item.getQuantity();
            variant.setStockQuantity(restoredQty);
            variantRepo.save(variant);
            log.info("Stock re-credited: {} → {} units (was {})",
                variant.getSku(), restoredQty,
                restoredQty - item.getQuantity());
        }

        // Mark bill cancelled
        bill.setStatus(BillStatus.CANCELLED);
        bill.setCancelledAt(LocalDateTime.now());
        bill.setCancelledBy(phone);
        billRepo.save(bill);

        auditRepo.save(AuditLog.of(phone, AuditAction.BILL_CANCELLED,
            "Cancelled: " + billNumber, true));

        log.info("Bill {} cancelled by {}", billNumber, phone);

        // Notify shopkeeper
        twilioMessaging.send(phone,
            "Bill *" + billNumber + "* cancelled.\n" +
            "Stock for " + bill.getItems().size() + " item(s) has been restored.\n\n" +
            "Generating cancellation note...");

        // Generate and send cancellation note PDF
        invoiceDelivery.deliverCancellationNote(bill, phone);
    }

    /**
     * Partial return: cancels specific items from a confirmed bill,
     * re-credits only those items' stock, and issues a credit note.
     */
    @Transactional
    public void partialReturn(String phone, String billNumber,
                               String sku, int returnQty) {
        Optional<Bill> billOpt = billRepo.findByBillNumber(billNumber);

        if (billOpt.isEmpty()) {
            twilioMessaging.send(phone,
                "Bill *" + billNumber + "* not found.");
            return;
        }

        Bill bill = billOpt.get();

        if (!bill.getShopkeeperPhone().equals(phone)) {
            twilioMessaging.send(phone,
                "You can only process returns for bills you created.");
            return;
        }

        // Find the item in the bill
        Optional<BillItem> itemOpt = bill.getItems().stream()
            .filter(i -> i.getVariant().getSku()
                .equalsIgnoreCase(sku))
            .findFirst();

        if (itemOpt.isEmpty()) {
            twilioMessaging.send(phone,
                "SKU *" + sku + "* not found in bill *" + billNumber + "*.");
            return;
        }

        BillItem item = itemOpt.get();

        if (returnQty > item.getQuantity()) {
            twilioMessaging.send(phone,
                "Cannot return " + returnQty + " units — bill only has "
                + item.getQuantity() + " units of " + sku + ".");
            return;
        }

        // Re-credit the returned quantity
        ProductVariant variant = item.getVariant();
        variant.setStockQuantity(variant.getStockQuantity() + returnQty);
        variantRepo.save(variant);

        // Update or remove line item
        if (returnQty == item.getQuantity()) {
            bill.getItems().remove(item);
            billItemRepo.delete(item);
        } else {
            item.setQuantity(item.getQuantity() - returnQty);
            item.setLineTotal(item.getUnitPrice().multiply(
                java.math.BigDecimal.valueOf(item.getQuantity())));
            billItemRepo.save(item);
        }

        // Recalculate bill totals
        recalculateBillTotals(bill);
        billRepo.save(bill);

        auditRepo.save(AuditLog.of(phone, AuditAction.BILL_CANCELLED,
            "Partial return: " + returnQty + "x " + sku +
            " from " + billNumber, true));

        log.info("Partial return: {}x {} from {} by {}",
            returnQty, sku, billNumber, phone);

        twilioMessaging.send(phone,
            String.format(
                "Return processed:\n" +
                "%dx %s returned from bill *%s*\n" +
                "Stock updated. New grand total: *₹%,.0f*",
                returnQty, sku, billNumber, bill.getGrandTotal()));
    }

    private void recalculateBillTotals(Bill bill) {
        java.math.BigDecimal sub = bill.getItems().stream()
            .map(BillItem::getLineTotal)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal gst = sub.multiply(new java.math.BigDecimal("0.18"))
            .setScale(2, java.math.RoundingMode.HALF_UP);
        bill.setTotalAmount(sub);
        bill.setTaxAmount(gst);
        bill.setGrandTotal(sub.add(gst));
    }
}