package com.billing.repository;

import com.billing.model.AuditLog;
import com.billing.model.AuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    // Count bills in a rolling time window — used by rate limiter
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.phone = :phone " +
           "AND a.action = 'BILL_CONFIRMED' AND a.timestamp > :since")
    long countConfirmedBillsSince(String phone, LocalDateTime since);

    List<AuditLog> findByPhoneOrderByTimestampDesc(String phone);
}