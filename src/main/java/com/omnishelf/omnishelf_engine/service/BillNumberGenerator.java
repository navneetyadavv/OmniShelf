package com.omnishelf.engine.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates GST-compliant sequential invoice numbers in the format:
 * OMNI-YYYYMMDD-NNNN  e.g. OMNI-20240615-0001
 *
 * The counter resets each calendar day. Thread-safe via AtomicInteger.
 */
@Component
public class BillNumberGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicInteger counter = new AtomicInteger(0);
    private volatile String     currentDay = LocalDate.now().format(DATE_FMT);

    public synchronized String next() {
        String today = LocalDate.now().format(DATE_FMT);
        if (!today.equals(currentDay)) {
            currentDay = today;
            counter.set(0);
        }
        return "OMNI-" + today + "-" + String.format("%04d", counter.incrementAndGet());
    }
}
