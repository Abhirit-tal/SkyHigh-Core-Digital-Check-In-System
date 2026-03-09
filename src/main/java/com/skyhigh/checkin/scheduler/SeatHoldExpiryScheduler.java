package com.skyhigh.checkin.scheduler;

import com.skyhigh.checkin.dto.event.SeatReleasedEvent;
import com.skyhigh.checkin.model.entity.Seat;
import com.skyhigh.checkin.model.entity.SeatAuditLog;
import com.skyhigh.checkin.model.enums.SeatStatus;
import com.skyhigh.checkin.repository.SeatAuditLogRepository;
import com.skyhigh.checkin.repository.SeatRepository;
import com.skyhigh.checkin.service.SeatEventPublisher;
import com.skyhigh.checkin.service.SeatLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeatHoldExpiryScheduler {

    private final SeatRepository seatRepository;
    private final SeatAuditLogRepository auditLogRepository;
    private final SeatLockService seatLockService;
    private final SeatEventPublisher seatEventPublisher;

    /**
     * Runs every 10 seconds to release expired seat holds.
     * This is a backup mechanism - Redis TTL should handle most expirations.
     * After releasing, publishes event to trigger waitlist processing.
     *
     * @SchedulerLock ensures only one instance runs this in a multi-instance deployment.
     */
    @Scheduled(fixedRate = 10000)
    @SchedulerLock(name = "releaseExpiredSeatHolds", lockAtLeastFor = "5s", lockAtMostFor = "2m")
    @Transactional
    public void releaseExpiredSeatHolds() {
        LocalDateTime now = LocalDateTime.now();
        List<Seat> expiredSeats = seatRepository.findExpiredHolds(now);

        if (expiredSeats.isEmpty()) {
            return;
        }

        log.info("Found {} expired seat holds to release", expiredSeats.size());

        for (Seat seat : expiredSeats) {
            try {
                releaseSeat(seat);
            } catch (Exception e) {
                log.error("Error releasing seat hold for seat {}: {}", seat.getId(), e.getMessage());
            }
        }
    }

    private void releaseSeat(Seat seat) {
        String previousStatus = seat.getStatus().name();
        UUID previousHolderId = seat.getHeldByPassenger() != null ? seat.getHeldByPassenger().getId() : null;

        // Release Redis lock if exists
        seatLockService.forceReleaseLock(seat.getFlight().getId(), seat.getSeatNumber());

        // Update database
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setHeldByPassenger(null);
        seat.setHeldUntil(null);
        seatRepository.save(seat);

        // Audit log
        SeatAuditLog auditLog = SeatAuditLog.builder()
                .seatId(seat.getId())
                .flightId(seat.getFlight().getId())
                .seatNumber(seat.getSeatNumber())
                .previousStatus(previousStatus)
                .newStatus("AVAILABLE")
                .changeReason("Seat hold expired (scheduler)")
                .build();
        auditLogRepository.save(auditLog);

        // Publish event to trigger waitlist processing
        SeatReleasedEvent event = SeatReleasedEvent.builder()
                .flightId(seat.getFlight().getId())
                .seatId(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .seatClass(seat.getSeatClass().name())
                .reason("HOLD_EXPIRED")
                .previousHolderId(previousHolderId)
                .releasedAt(LocalDateTime.now())
                .build();
        seatEventPublisher.publishSeatReleased(event);

        log.info("Released expired seat hold: {} on flight {}", seat.getSeatNumber(), seat.getFlight().getFlightNumber());
    }
}

