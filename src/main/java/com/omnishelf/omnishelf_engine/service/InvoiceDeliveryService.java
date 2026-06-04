package com.omnishelf.omnishelf_engine.service;

import com.omnishelf.omnishelf_engine.exception.PdfGenerationException;
import com.omnishelf.omnishelf_engine.model.Bill;
import com.omnishelf.omnishelf_engine.model.BillItem;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InvoiceDeliveryService {

    @Value("${twilio.whatsapp-number}")
    private String fromNumber;

    private final InvoicePdfBuilder   pdfBuilder;
    private final PdfHostingService   pdfHosting;
    private final TwilioMessagingService twilioMessaging;

    public InvoiceDeliveryService(InvoicePdfBuilder pdfBuilder,
                                   PdfHostingService pdfHosting,
                                   TwilioMessagingService twilioMessaging) {
        this.pdfBuilder      = pdfBuilder;
        this.pdfHosting      = pdfHosting;
        this.twilioMessaging = twilioMessaging;
    }

    /**
     * Full pipeline: Bill → PDF bytes → hosted URL → WhatsApp media message.
     * Falls back to a text summary if PDF generation fails.
     */
    public void deliverInvoice(Bill bill, String toPhone) {
        try {
            // 1. Generate PDF in memory
            byte[] pdfBytes = pdfBuilder.buildInvoice(bill);

            // 2. Host it temporarily and get a public URL
            String pdfUrl = pdfHosting.store(pdfBytes);
            log.info("Delivering invoice {} to {} via URL: {}",
                bill.getBillNumber(), toPhone, pdfUrl);

            // 3. Send as WhatsApp media message
            Message message = Message.creator(
                new PhoneNumber("whatsapp:" + toPhone),
                new PhoneNumber(fromNumber),
                buildInvoiceCaption(bill)
            )
            .setMediaUrl(java.util.List.of(
                URI.create(pdfUrl)
            ))
            .create();

            log.info("Invoice delivered. Twilio SID: {}", message.getSid());

            // 4. Confirm delivery to shopkeeper
            twilioMessaging.send(toPhone,
                "Invoice *" + bill.getBillNumber() + "* sent!\n\n" +
                "Grand total: *₹" + String.format("%,.0f", bill.getGrandTotal()) + "*\n\n" +
                "Start a new bill anytime.");

        } catch (PdfGenerationException e) {
            log.error("PDF generation failed for bill {}: {}",
                bill.getBillNumber(), e.getMessage());
            deliverTextFallback(bill, toPhone);

        } catch (Exception e) {
            log.error("Invoice delivery failed for {}: {}", toPhone, e.getMessage(), e);
            twilioMessaging.send(toPhone,
                "Invoice generated but delivery failed.\n" +
                "Bill No: " + bill.getBillNumber() +
                "\nPlease contact support.");
        }
    }

    /**
     * Fallback: if PDF fails, send a plain-text invoice summary.
     * The sale is already confirmed — shopkeeper must not lose the bill.
     */
    private void deliverTextFallback(Bill bill, String toPhone) {
        StringBuilder sb = new StringBuilder();
        sb.append("*INVOICE — ").append(bill.getBillNumber()).append("*\n");
        sb.append(bill.getConfirmedAt()
            .format(java.time.format.DateTimeFormatter
                .ofPattern("dd MMM yyyy, hh:mm a"))).append("\n\n");

        for (BillItem item : bill.getItems()) {
            sb.append(String.format("%dx %s — ₹%,.0f\n",
                item.getQuantity(),
                item.getVariant().getSku(),
                item.getLineTotal()));
        }

        sb.append(String.format("\nSubtotal: ₹%,.0f", bill.getTotalAmount()));
        sb.append(String.format("\nGST (18%%): ₹%,.0f", bill.getTaxAmount()));
        sb.append(String.format("\n*Total: ₹%,.0f*", bill.getGrandTotal()));
        sb.append("\n\n_PDF unavailable — saved in system_");

        twilioMessaging.send(toPhone, sb.toString());
        log.info("Text fallback invoice sent for bill {}", bill.getBillNumber());
    }

    private String buildInvoiceCaption(Bill bill) {
        return String.format(
            "Invoice *%s* | %s\nTotal: *₹%,.0f* (incl. GST)\n\nPDF attached below.",
            bill.getBillNumber(),
            bill.getConfirmedAt()
                .format(java.time.format.DateTimeFormatter
                    .ofPattern("dd MMM yyyy")),
            bill.getGrandTotal()
        );
    }
}