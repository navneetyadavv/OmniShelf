package com.omnishelf.omnishelf_engine.repository;

import com.omnishelf.omnishelf_engine.model.BillSession;
import com.omnishelf.omnishelf_engine.model.SessionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface BillSessionRepository extends JpaRepository<BillSession, String> {

    // Find the active session for a phone number
    Optional<BillSession> findByShopkeeperPhoneAndStateIn(
        String phone, java.util.List<SessionState> states);

    // Cleanup job — find all expired sessions
    @Query("SELECT s FROM BillSession s WHERE s.expiresAt < :now AND s.state = 'ACTIVE'")
    java.util.List<BillSession> findExpiredSessions(LocalDateTime now);

    // Hard delete confirmed/cancelled sessions older than 24h
    @Modifying
    @Query("DELETE FROM BillSession s WHERE s.lastActivityAt < :cutoff " +
           "AND s.state IN ('CONFIRMED','CANCELLED')")
    void deleteOldClosedSessions(LocalDateTime cutoff);
}