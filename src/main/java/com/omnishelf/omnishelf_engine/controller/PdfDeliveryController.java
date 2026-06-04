package com.omnishelf.omnishelf_engine.controller;

import com.omnishelf.omnishelf_engine.service.PdfHostingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/invoices")
@Slf4j
public class PdfDeliveryController {

    private final PdfHostingService pdfHosting;

    public PdfDeliveryController(PdfHostingService pdfHosting) {
        this.pdfHosting = pdfHosting;
    }

    @GetMapping(value = "/{token}.pdf",
                produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> servePdf(@PathVariable String token) {
        byte[] pdf = pdfHosting.retrieve(token);

        if (pdf == null) {
            log.warn("PDF token not found or already consumed: {}", token);
            return ResponseEntity.notFound().build();
        }

        // Delete after serve — single-use token
        pdfHosting.delete(token);

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\"invoice.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdf.length)
            .body(pdf);
    }
}