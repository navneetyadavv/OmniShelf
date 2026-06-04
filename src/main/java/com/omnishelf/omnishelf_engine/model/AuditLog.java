package com.billing.model;

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
    private String phone;           // who did it

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;     // what they did

    @Column
    private String detail;          // extra context (bill number, amount, etc.)

    @Column(nullable = false)
    private boolean success;        // did it succeed or was it blocked?

    @Column
    private String blockedReason;   // why it was blocked if success=false

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    // Factory method for clean creation
    public static AuditLog of(String phone, AuditAction action,
                               String detail, boolean success) {
        AuditLog log = new AuditLog();
        log.phone   = phone;
        log.action  = action;
        log.detail  = detail;
        log.success = success;
        return log;
    }

    public static AuditLog blocked(String phone, AuditAction action,
                                    String reason) {
        AuditLog log = of(phone, action, null, false);
        log.blockedReason = reason;
        return log;
    }
}