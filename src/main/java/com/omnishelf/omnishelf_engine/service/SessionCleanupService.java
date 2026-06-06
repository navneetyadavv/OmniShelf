package com.omnishelf.engine.service;

import com.omnishelf.engine.model.BillSession;
import com.omnishelf.engine.model.BillStatus;
import com.omnishelf.engine.model.SessionState;
import com.omnishelf.engine.repository.BillRepository;
import com.omnishelf.engine.repository.BillSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SessionCleanupService {

    private final BillSessionRepository   sessionRepo;
    private final BillRepository          billRepo;
    private final TwilioMessagingService  twilioMessaging;

    /**
     * Runs every 5 minutes. Finds sessions whose expiresAt is in the past
     * and marks them (and their draft bills) as CANCELLED.
     * Sends a WhatsApp notification to the shopkeeper.
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    @Transactional
    public void expireInactiveSessions() {
        List<BillSession> expired = sessionRepo.findExpiredSessions(LocalDateTime.now());

        for (BillSession session : expired) {
            log.info("Auto-expiring session for {}", session.getShopkeeperPhone());

            if (session.getBill() != null) {
                session.getBill().setStatus(BillStatus.CANCELLED);
                billRepo.save(session.getBill());
            }

            session.setState(SessionState.CANCELLED);
            sessionRepo.save(session);

            twilioMessaging.send(session.getShopkeeperPhone(),
                "⏰ Your billing session expired after 20 minutes of inactivity.\n" +
                "The draft bill was discarded. Send items to start a new one.");
        }

        if (!expired.isEmpty()) {
            log.info("Expired {} inactive sessions", expired.size());
        }
    }

    /**
     * Runs every night at 2 AM. Cleans up old CONFIRMED/CANCELLED session records.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeOldClosedSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        sessionRepo.deleteOldClosedSessions(cutoff);
        log.info("Purged closed sessions older than 30 days");
    }
}
