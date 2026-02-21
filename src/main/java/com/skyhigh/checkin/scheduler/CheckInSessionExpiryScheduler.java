package com.skyhigh.checkin.scheduler;

import com.skyhigh.checkin.model.entity.CheckIn;
import com.skyhigh.checkin.model.enums.CheckInStatus;
import com.skyhigh.checkin.repository.CheckInRepository;
import com.skyhigh.checkin.service.SeatLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckInSessionExpiryScheduler {

    private final CheckInRepository checkInRepository;
    private final SeatLockService seatLockService;

    /**
     * Runs every minute to expire inactive check-in sessions.
     */
    @Scheduled(fixedRate = 60000) // 1 minute
    @Transactional
    public void expireInactiveSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<CheckIn> expiredSessions = checkInRepository.findExpiredSessions(now);

        if (expiredSessions.isEmpty()) {
            return;
        }

        log.info("Found {} expired check-in sessions", expiredSessions.size());

        for (CheckIn checkIn : expiredSessions) {
            try {
                expireSession(checkIn);
            } catch (Exception e) {
                log.error("Error expiring check-in session {}: {}", checkIn.getId(), e.getMessage());
            }
        }
    }

    private void expireSession(CheckIn checkIn) {
        // Release held seat if any
        if (checkIn.getSeat() != null && checkIn.getSeat().isHeld()) {
            seatLockService.forceReleaseLock(
                    checkIn.getBooking().getFlight().getId(),
                    checkIn.getSeat().getSeatNumber()
            );

            // The seat will be released by SeatHoldExpiryScheduler
        }

        checkIn.setStatus(CheckInStatus.EXPIRED);
        checkInRepository.save(checkIn);

        log.info("Expired check-in session: {} for booking {}",
                checkIn.getId(), checkIn.getBooking().getBookingReference());
    }
}

