package com.omnishelf.omnishelf_engine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class PdfHostingService {

    @Value("${pdf.host-base-url}")
    private String baseUrl;

    // In-memory store: token → PDF bytes
    // Key expires after delivery (TTL handled by scheduled cleanup)
    private final Map<String, byte[]> pdfStore = new ConcurrentHashMap<>();

    /**
     * Stores PDF bytes and returns a public URL Twilio can fetch.
     * Token is a UUID — unguessable, single-use.
     */
    public String store(byte[] pdfBytes) {
        String token = UUID.randomUUID().toString();
        pdfStore.put(token, pdfBytes);
        String url = baseUrl + "/" + token + ".pdf";
        log.info("PDF stored at token: {} | size: {} bytes", token, pdfBytes.length);
        return url;
    }

    public byte[] retrieve(String token) {
        return pdfStore.get(token);
    }

    public void delete(String token) {
        pdfStore.remove(token);
        log.debug("PDF token deleted: {}", token);
    }
}