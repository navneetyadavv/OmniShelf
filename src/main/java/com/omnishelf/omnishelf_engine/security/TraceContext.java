package com.omnishelf.engine.security;

import org.slf4j.MDC;
import java.util.UUID;

/**
 * Thread-local correlation ID for structured logging.
 * Each incoming WhatsApp message gets a unique traceId that flows
 * through all service calls, making it trivial to reconstruct what
 * happened for any given bill or message.
 */
public final class TraceContext {

    private static final String TRACE_KEY = "traceId";

    private TraceContext() {}

    public static String init() {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MDC.put(TRACE_KEY, id);
        return id;
    }

    public static String get() {
        String id = MDC.get(TRACE_KEY);
        return id != null ? id : "no-trace";
    }

    public static void clear() {
        MDC.remove(TRACE_KEY);
    }
}
