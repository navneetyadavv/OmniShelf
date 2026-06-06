package com.omnishelf.engine.repository;

import com.omnishelf.engine.model.BillSession;
import com.omnishelf.engine.model.SessionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BillSessionRepository extends JpaRepository<BillSession, String> {

    Optional<BillSession> findByShopkeeperPhoneAndStateIn(
        String phone, List<SessionState> states);

    @Query("SELECT s FROM BillSession s WHERE s.expiresAt < :now " +
           "AND s.state IN ('ACTIVE', 'AWAITING_CONFIRMATION')")
    List<BillSession> findExpiredSessions(LocalDateTime now);

    @Modifying
    @Query("DELETE FROM BillSession s WHERE s.lastActivityAt < :cutoff " +
           "AND s.state IN ('CONFIRMED', 'CANCELLED')")
    void deleteOldClosedSessions(LocalDateTime cutoff);
}
