package com.omnishelf.engine.repository;

import com.omnishelf.engine.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.phone = :phone " +
           "AND a.action = 'BILL_CONFIRMED' AND a.timestamp >= :since AND a.success = true")
    long countConfirmedBillsSince(String phone, LocalDateTime since);
}
