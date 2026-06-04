package com.billing.service;

import com.billing.model.Bill;
import com.billing.model.BillStatus;
import com.billing.repository.BillRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class ReprintService {

    private final BillRepository         billRepo;
    private final InvoiceDeliveryService invoiceDelivery;
    private final TwilioMessagingService twilioMessaging;

    public ReprintService(BillRepository billRepo,
                          InvoiceDeliveryService invoiceDelivery,
                          TwilioMessagingService twilioMessaging) {
        this.billRepo        = billRepo;
        this.invoiceDelivery = invoiceDelivery;
        this.twilioMessaging = twilioMessaging;
    }

    /**
     * Re-generates and re-sends a PDF for any confirmed bill.
     * The original bill data is unchanged — this is a pure reprint.
     */
    public void reprintInvoice(String phone, String billNumber) {
        Optional<Bill> billOpt = billRepo.findByBillNumber(billNumber);

        if (billOpt.isEmpty()) {
            twilioMessaging.send(phone,
                "Bill *" + billNumber + "* not found.\n" +
                "Check the bill number and try again.");
            return;
        }

        Bill bill = billOpt.get();

        // Security — only the creator can reprint
        if (!bill.getShopkeeperPhone().equals(phone)) {
            twilioMessaging.send(phone,
                "You can only reprint bills you created.");
            return;
        }

        if (bill.getStatus() == BillStatus.CANCELLED) {
            twilioMessaging.send(phone,
                "Bill *" + billNumber + "* was cancelled — " +
                "sending cancellation note instead.");
            invoiceDelivery.deliverCancellationNote(bill, phone);
            return;
        }

        if (bill.getStatus() != BillStatus.CONFIRMED) {
            twilioMessaging.send(phone,
                "Bill *" + billNumber + "* is not confirmed yet.");
            return;
        }

        log.info("Reprinting bill {} for {}", billNumber, phone);
        twilioMessaging.send(phone,
            "Resending invoice *" + billNumber + "*...");

        // Reuse Phase 4's full delivery pipeline
        invoiceDelivery.deliverInvoice(bill, phone);
    }

    /**
     * Lists the last 5 confirmed bills for a phone number.
     * Handy when the shopkeeper doesn't remember the bill number.
     */
    public void listRecentBills(String phone) {
        java.util.List<Bill> recent = billRepo
            .findTop5ByShopkeeperPhoneAndStatusOrderByConfirmedAtDesc(
                phone, BillStatus.CONFIRMED);

        if (recent.isEmpty()) {
            twilioMessaging.send(phone, "No confirmed bills found.");
            return;
        }

        StringBuilder sb = new StringBuilder("*Your recent bills:*\n\n");
        for (Bill bill : recent) {
            sb.append(String.format("• *%s* — ₹%,.0f — %s\n",
                bill.getBillNumber(),
                bill.getGrandTotal(),
                bill.getConfirmedAt().format(
                    java.time.format.DateTimeFormatter
                        .ofPattern("dd MMM, hh:mm a"))));
        }
        sb.append("\nReply *resend BILL-XXXXXX* to reprint any bill.");
        twilioMessaging.send(phone, sb.toString());
    }
}