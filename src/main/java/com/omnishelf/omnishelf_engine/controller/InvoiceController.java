package com.omnishelf.engine.controller;

import com.omnishelf.engine.model.Bill;
import com.omnishelf.engine.model.BillStatus;
import com.omnishelf.engine.repository.BillRepository;
import com.omnishelf.engine.repository.ProductVariantRepository;
import com.omnishelf.engine.service.PdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@Slf4j
@RequiredArgsConstructor
public class InvoiceController {

    private final BillRepository           billRepo;
    private final ProductVariantRepository variantRepo;
    private final PdfService               pdfService;

    private static final Path PDF_DIR = Path.of("invoices");

    /**
     * Serves the PDF file from disk — this URL is what Twilio sends as the media URL.
     */
    @GetMapping("/invoices/{filename:.+}")
    public ResponseEntity<Resource> servePdf(@PathVariable String filename) {
        try {
            Path filePath = PDF_DIR.resolve(filename).normalize();
            if (!filePath.startsWith(PDF_DIR)) {
                return ResponseEntity.badRequest().build(); // path traversal guard
            }

            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] data = Files.readAllBytes(filePath);
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"" + filename + "\"")
                .body(new ByteArrayResource(data));

        } catch (IOException e) {
            log.error("Failed to serve PDF {}: {}", filename, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Re-generates PDF on demand (useful for reprints when file is missing).
     */
    @GetMapping("/api/bills/{billNumber}/pdf")
    public ResponseEntity<Resource> regeneratePdf(@PathVariable String billNumber) {
        Optional<Bill> billOpt = billRepo.findByBillNumber(billNumber);
        if (billOpt.isEmpty()) return ResponseEntity.notFound().build();

        Bill bill = billOpt.get();
        if (bill.getStatus() != BillStatus.CONFIRMED) {
            return ResponseEntity.badRequest().build();
        }

        byte[] pdfBytes = pdfService.generateGstInvoice(bill);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + billNumber + ".pdf\"")
            .body(new ByteArrayResource(pdfBytes));
    }

    // ── Dashboard REST APIs ────────────────────────────────────────────────────

    @GetMapping("/api/dashboard/summary")
    public ResponseEntity<Map<String, Object>> dashboardSummary() {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay   = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalConfirmedBills", billRepo.countByStatus(BillStatus.CONFIRMED));
        summary.put("todayRevenue",
            billRepo.sumRevenueByDateRange(startOfDay, endOfDay).orElse(java.math.BigDecimal.ZERO));
        summary.put("todayBills",
            billRepo.countConfirmedBillsByDateRange(startOfDay, endOfDay));
        summary.put("lowStockCount",
            variantRepo.findLowStockVariants(5).size());
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/api/bills")
    public ResponseEntity<List<Bill>> listBills(
            @RequestParam(defaultValue = "CONFIRMED") BillStatus status) {
        return ResponseEntity.ok(billRepo.findByStatus(status));
    }

    @GetMapping("/api/bills/{billNumber}")
    public ResponseEntity<Bill> getBill(@PathVariable String billNumber) {
        return billRepo.findByBillNumber(billNumber)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/inventory/low-stock")
    public ResponseEntity<?> getLowStock(
            @RequestParam(defaultValue = "5") int threshold) {
        return ResponseEntity.ok(variantRepo.findLowStockVariants(threshold));
    }
}
