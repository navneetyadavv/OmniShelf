package com.omnishelf.engine.service;

import com.omnishelf.engine.exception.PdfGenerationException;
import com.omnishelf.engine.model.Bill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class PdfDeliveryService {

    @Value("${pdf.host-base-url}")
    private String pdfHostBaseUrl;

    private static final Path PDF_DIR = Path.of("invoices");

    private final PdfService             pdfService;
    private final TwilioMessagingService twilioMessaging;

    /**
     * Generates and delivers the invoice PDF asynchronously.
     * This runs in a separate thread so the webhook returns immediately
     * to Twilio, avoiding timeout-triggered duplicate webhook retries.
     */
    @Async("taskExecutor")
    public void generateAndSend(Bill bill, String recipientPhone) {
        try {
            Files.createDirectories(PDF_DIR);
            byte[] pdfBytes = pdfService.generateGstInvoice(bill);

            String filename = bill.getBillNumber() + ".pdf";
            Path   filePath = PDF_DIR.resolve(filename);
            Files.write(filePath, pdfBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            String publicUrl = pdfHostBaseUrl + "/" + filename;

            // Send as media URL via Twilio
            sendWithRetry(recipientPhone, publicUrl, bill.getBillNumber(), 3);

        } catch (PdfGenerationException e) {
            log.error("PDF generation failed for {}: {}", bill.getBillNumber(), e.getMessage());
            twilioMessaging.send(recipientPhone,
                "⚠ Could not generate PDF for " + bill.getBillNumber() +
                ". Reply *REPRINT " + bill.getBillNumber() + "* to try again.");
        } catch (IOException e) {
            log.error("PDF file write failed: {}", e.getMessage());
            twilioMessaging.send(recipientPhone,
                "⚠ Bill confirmed but PDF delivery failed. Reply *REPRINT " +
                bill.getBillNumber() + "* to retry.");
        }
    }

    private void sendWithRetry(String phone, String pdfUrl,
                                String billNumber, int maxAttempts) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Send the URL as a text message — Twilio renders it inline
                twilioMessaging.send(phone,
                    String.format("🧾 Invoice *%s* is ready:\n%s\n\n" +
                        "Reply *REPRINT %s* to resend.", billNumber, pdfUrl, billNumber));
                log.info("PDF link sent to {} for bill {} (attempt {})", phone, billNumber, attempt);
                return;
            } catch (Exception e) {
                log.warn("PDF delivery attempt {}/{} failed: {}", attempt, maxAttempts, e.getMessage());
                if (attempt == maxAttempts) {
                    log.error("All PDF delivery attempts exhausted for bill {}", billNumber);
                    // Final fallback: plain text notification
                    twilioMessaging.send(phone,
                        "✅ Bill " + billNumber + " confirmed, but PDF link failed to deliver. " +
                        "Reply *REPRINT " + billNumber + "* to retry.");
                } else {
                    try { Thread.sleep(1000L * attempt); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }
}
