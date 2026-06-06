package com.omnishelf.engine.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column
    private String detail;

    @Column(nullable = false)
    private boolean success;

    @Column
    private String blockedReason;

    // Correlation ID — links this audit entry to its request chain
    @Column
    private String traceId;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public static AuditLog of(String phone, AuditAction action,
                               String detail, boolean success) {
        AuditLog log = new AuditLog();
        log.phone   = phone;
        log.action  = action;
        log.detail  = detail;
        log.success = success;
        return log;
    }

    public static AuditLog of(String phone, AuditAction action,
                               String detail, boolean success, String traceId) {
        AuditLog log = of(phone, action, detail, success);
        log.traceId = traceId;
        return log;
    }

    public static AuditLog blocked(String phone, AuditAction action, String reason) {
        AuditLog log = of(phone, action, null, false);
        log.blockedReason = reason;
        return log;
    }
}
