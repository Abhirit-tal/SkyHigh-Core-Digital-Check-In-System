package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
import com.skyhigh.checkin.dto.event.SeatReleasedEvent;
import com.skyhigh.checkin.dto.response.SeatHoldResponse;
import com.skyhigh.checkin.dto.response.SeatMapResponse;
import com.skyhigh.checkin.exception.*;
import com.skyhigh.checkin.model.entity.CheckIn;
import com.skyhigh.checkin.model.entity.Passenger;
import com.skyhigh.checkin.model.entity.Seat;
import com.skyhigh.checkin.model.entity.SeatAuditLog;
import com.skyhigh.checkin.model.enums.SeatClass;
import com.skyhigh.checkin.model.enums.SeatStatus;
import com.skyhigh.checkin.repository.CheckInRepository;
import com.skyhigh.checkin.repository.PassengerRepository;
import com.skyhigh.checkin.repository.SeatAuditLogRepository;
import com.skyhigh.checkin.repository.SeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SeatService {

    private final SeatRepository seatRepository;
    private final PassengerRepository passengerRepository;
    private final CheckInRepository checkInRepository;
    private final SeatAuditLogRepository auditLogRepository;
    private final SeatLockService seatLockService;
    private final SeatEventPublisher seatEventPublisher;
    private final SeatDomainEventPublisher seatDomainEventPublisher;
    private final CheckInConfig checkInConfig;
    private final Counter seatHoldCounter;
    private final Counter seatConfirmCounter;
    private final Counter seatCancelCounter;

    public SeatService(SeatRepository seatRepository,
                       PassengerRepository passengerRepository,
                       CheckInRepository checkInRepository,
                       SeatAuditLogRepository auditLogRepository,
                       SeatLockService seatLockService,
                       SeatEventPublisher seatEventPublisher,
                       SeatDomainEventPublisher seatDomainEventPublisher,
                       CheckInConfig checkInConfig,
                       MeterRegistry meterRegistry) {
        this.seatRepository = seatRepository;
        this.passengerRepository = passengerRepository;
        this.checkInRepository = checkInRepository;
        this.auditLogRepository = auditLogRepository;
        this.seatLockService = seatLockService;
        this.seatEventPublisher = seatEventPublisher;
        this.seatDomainEventPublisher = seatDomainEventPublisher;
        this.checkInConfig = checkInConfig;
        this.seatHoldCounter = Counter.builder("skyhigh.seats.holds")
                .description("Number of seat holds").register(meterRegistry);
        this.seatConfirmCounter = Counter.builder("skyhigh.seats.confirms")
                .description("Number of seat confirmations").register(meterRegistry);
        this.seatCancelCounter = Counter.builder("skyhigh.seats.cancellations")
                .description("Number of seat cancellations").register(meterRegistry);
    }

    @Cacheable(value = "seatMap", key = "#flightId")
    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(UUID flightId) {
        log.info("Fetching seat map for flight: {}", flightId);

        List<Seat> seats = seatRepository.findByFlightIdOrderBySeatClassAndNumber(flightId);

        if (seats.isEmpty()) {
            throw new ResourceNotFoundException("Seats for flight", flightId);
        }

        Map<SeatClass, List<SeatMapResponse.SeatInfo>> seatsByClass = seats.stream()
                .map(this::mapToSeatInfo)
                .collect(Collectors.groupingBy(SeatMapResponse.SeatInfo::getSeatClass));

        Map<SeatClass, Long> availableByClass = seats.stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .collect(Collectors.groupingBy(Seat::getSeatClass, Collectors.counting()));

        long total = seats.size();
        long available = seats.stream().filter(Seat::isAvailable).count();
        long held = seats.stream().filter(Seat::isHeld).count();
        long confirmed = seats.stream().filter(Seat::isConfirmed).count();

        return SeatMapResponse.builder()
                .flightId(flightId)
                .flightNumber(seats.get(0).getFlight().getFlightNumber())
                .seatsByClass(seatsByClass)
                .summary(SeatMapResponse.SeatSummary.builder()
                        .total(total)
                        .available(available)
                        .held(held)
                        .confirmed(confirmed)
                        .availableByClass(availableByClass)
                        .build())
                .retrievedAt(LocalDateTime.now())
                .build();
    }

    @CacheEvict(value = "seatMap", allEntries = true)
    @Transactional
    public SeatHoldResponse holdSeat(UUID seatId, UUID passengerId, UUID checkInId) {
        log.info("Attempting to hold seat {} for passenger {} (check-in: {})", seatId, passengerId, checkInId);

        Seat seat = seatRepository.findByIdWithLock(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", seatId));

        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger", passengerId));

        CheckIn checkIn = checkInRepository.findById(checkInId)
                .orElseThrow(() -> new ResourceNotFoundException("Check-in", checkInId));

        // Check if seat is available
        if (seat.isConfirmed()) {
            throw new SeatAlreadyConfirmedException(seat.getFlight().getId(), seat.getSeatNumber());
        }

        if (seat.isHeld() && !seat.isHoldExpired()) {
            if (!seat.isHeldByPassenger(passengerId)) {
                throw new SeatAlreadyHeldException(seat.getFlight().getId(), seat.getSeatNumber(), seat.getHeldUntil());
            }
            // Already held by this passenger - extend the hold
            log.info("Seat {} already held by passenger {}, extending hold", seatId, passengerId);
        }

        // Try to acquire Redis lock
        boolean lockAcquired = seatLockService.acquireLock(
                seat.getFlight().getId(),
                seat.getSeatNumber(),
                passengerId,
                checkInConfig.getSeatHoldDurationSeconds()
        );

        if (!lockAcquired && !seatLockService.isLockedByPassenger(seat.getFlight().getId(), seat.getSeatNumber(), passengerId)) {
            // Someone else holds the Redis lock
            throw new SeatAlreadyHeldException(seat.getFlight().getId(), seat.getSeatNumber(),
                    LocalDateTime.now().plusSeconds(checkInConfig.getSeatHoldDurationSeconds()));
        }

        // Release any previously held seat by this passenger for this check-in
        if (checkIn.getSeat() != null && !checkIn.getSeat().getId().equals(seatId)) {
            releasePreviousSeat(checkIn, passengerId);
        }

        // Update seat status
        String previousStatus = seat.getStatus().name();
        LocalDateTime heldUntil = LocalDateTime.now().plusSeconds(checkInConfig.getSeatHoldDurationSeconds());

        seat.setStatus(SeatStatus.HELD);
        seat.setHeldByPassenger(passenger);
        seat.setHeldUntil(heldUntil);
        seatRepository.save(seat);

        // Update check-in with selected seat
        checkIn.setSeat(seat);
        checkIn.updateActivity();
        checkInRepository.save(checkIn);

        // Audit log
        logSeatChange(seat, previousStatus, "HELD", passengerId, "Seat held by passenger");

        // Invalidate seat map cache
        evictSeatMapCache(seat.getFlight().getId());

        log.info("Seat {} successfully held for passenger {} until {}", seatId, passengerId, heldUntil);
        seatHoldCounter.increment();

        return SeatHoldResponse.builder()
                .seatId(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .seatClass(seat.getSeatClass())
                .status(seat.getStatus())
                .heldUntil(heldUntil)
                .holdDurationSeconds(checkInConfig.getSeatHoldDurationSeconds())
                .build();
    }

    @CacheEvict(value = "seatMap", allEntries = true)
    @Transactional
    public void releaseSeatHold(UUID seatId, UUID passengerId) {
        log.info("Releasing seat hold: {} by passenger: {}", seatId, passengerId);

        Seat seat = seatRepository.findByIdWithLock(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", seatId));

        if (!seat.isHeld()) {
            log.warn("Seat {} is not held, nothing to release", seatId);
            return;
        }

        if (!seat.isHeldByPassenger(passengerId)) {
            throw new InvalidSeatStateException("You do not hold this seat");
        }

        // Release Redis lock
        seatLockService.releaseLock(seat.getFlight().getId(), seat.getSeatNumber(), passengerId);

        // Update database
        String previousStatus = seat.getStatus().name();
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setHeldByPassenger(null);
        seat.setHeldUntil(null);
        seatRepository.save(seat);

        logSeatChange(seat, previousStatus, "AVAILABLE", passengerId, "Seat released by passenger");

        log.info("Seat {} released successfully", seatId);
    }

    @CacheEvict(value = "seatMap", allEntries = true)
    @Transactional
    public Seat confirmSeat(UUID seatId, UUID passengerId) {
        log.info("Confirming seat {} for passenger {}", seatId, passengerId);

        Seat seat = seatRepository.findByIdWithLock(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", seatId));

        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger", passengerId));

        // Validate seat state
        if (seat.isConfirmed()) {
            throw new SeatAlreadyConfirmedException(seat.getFlight().getId(), seat.getSeatNumber());
        }

        if (!seat.isHeld()) {
            throw new InvalidSeatStateException("Seat must be held before confirmation");
        }

        if (!seat.isHeldByPassenger(passengerId)) {
            throw new InvalidSeatStateException("You do not hold this seat");
        }

        if (seat.isHoldExpired()) {
            // Release the seat and throw exception
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setHeldByPassenger(null);
            seat.setHeldUntil(null);
            seatRepository.save(seat);
            seatLockService.forceReleaseLock(seat.getFlight().getId(), seat.getSeatNumber());
            throw new SeatHoldExpiredException(seat.getSeatNumber(), seat.getHeldUntil());
        }

        // Confirm the seat with optimistic locking
        try {
            String previousStatus = seat.getStatus().name();
            seat.setStatus(SeatStatus.CONFIRMED);
            seat.setConfirmedByPassenger(passenger);
            seat.setHeldByPassenger(null);
            seat.setHeldUntil(null);
            seat = seatRepository.save(seat);

            // Release Redis lock
            seatLockService.forceReleaseLock(seat.getFlight().getId(), seat.getSeatNumber());

            logSeatChange(seat, previousStatus, "CONFIRMED", passengerId, "Seat confirmed by passenger");

            log.info("Seat {} confirmed for passenger {}", seatId, passengerId);
            seatConfirmCounter.increment();
            return seat;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic lock failure while confirming seat {}", seatId);
            throw new InvalidSeatStateException("Seat was modified by another process. Please try again.");
        }
    }

    /**
     * Cancels a confirmed seat assignment. The seat transitions to CANCELLED,
     * then becomes AVAILABLE and is offered to waitlisted passengers via event.
     */
    @CacheEvict(value = "seatMap", allEntries = true)
    @Transactional
    public Seat cancelConfirmedSeat(UUID seatId, UUID passengerId) {
        log.info("Cancelling confirmed seat {} by passenger {}", seatId, passengerId);

        Seat seat = seatRepository.findByIdWithLock(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", seatId));

        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger", passengerId));

        if (!seat.isConfirmed()) {
            throw new InvalidSeatStateException("Seat is not in CONFIRMED state. Current state: " + seat.getStatus());
        }

        if (!seat.isConfirmedByPassenger(passengerId)) {
            throw new InvalidSeatStateException("You did not confirm this seat");
        }

        try {
            String previousStatus = seat.getStatus().name();

            // Log the cancellation transition for audit trail
            logSeatChange(seat, previousStatus, "CANCELLED", passengerId, "Seat cancelled by passenger");
            seatCancelCounter.increment();

            // Transition directly to AVAILABLE in a single atomic save
            // (avoids crash leaving seat stuck in CANCELLED state)
            seat.setCancelledByPassenger(passenger);
            seat.setCancelledAt(LocalDateTime.now());
            seat.setConfirmedByPassenger(null);
            seat.resetToAvailable();
            seat = seatRepository.save(seat);

            logSeatChange(seat, "CANCELLED", "AVAILABLE", passengerId, "Cancelled seat made available for waitlist");

            // Publish event AFTER transaction commits (prevents ghost events on rollback)
            final Seat savedSeat = seat;
            seatDomainEventPublisher.publishAfterCommit(SeatReleasedEvent.builder()
                    .flightId(savedSeat.getFlight().getId())
                    .seatId(savedSeat.getId())
                    .seatNumber(savedSeat.getSeatNumber())
                    .seatClass(savedSeat.getSeatClass().name())
                    .reason("CANCELLED")
                    .previousHolderId(passengerId)
                    .releasedAt(LocalDateTime.now())
                    .build());

            log.info("Seat {} cancelled and made available. Waitlist notified.", seatId);
            return seat;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic lock failure while cancelling seat {}", seatId);
            throw new InvalidSeatStateException("Seat was modified by another process. Please try again.");
        }
    }

    /**
     * Publishes a seat released event for waitlist processing.
     */
    public void publishSeatReleasedEvent(Seat seat, String reason, UUID previousHolderId) {
        SeatReleasedEvent event = SeatReleasedEvent.builder()
                .flightId(seat.getFlight().getId())
                .seatId(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .seatClass(seat.getSeatClass().name())
                .reason(reason)
                .previousHolderId(previousHolderId)
                .releasedAt(LocalDateTime.now())
                .build();
        seatEventPublisher.publishSeatReleased(event);
    }

    @Transactional(readOnly = true)
    public Seat getSeatById(UUID seatId) {
        return seatRepository.findById(seatId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", seatId));
    }

    private void releasePreviousSeat(CheckIn checkIn, UUID passengerId) {
        Seat previousSeat = checkIn.getSeat();
        if (previousSeat != null && previousSeat.isHeld() && previousSeat.isHeldByPassenger(passengerId)) {
            log.info("Releasing previously held seat: {}", previousSeat.getSeatNumber());
            seatLockService.releaseLock(previousSeat.getFlight().getId(), previousSeat.getSeatNumber(), passengerId);
            previousSeat.setStatus(SeatStatus.AVAILABLE);
            previousSeat.setHeldByPassenger(null);
            previousSeat.setHeldUntil(null);
            seatRepository.save(previousSeat);
            logSeatChange(previousSeat, "HELD", "AVAILABLE", passengerId, "Seat released - passenger selected different seat");
        }
    }

    private SeatMapResponse.SeatInfo mapToSeatInfo(Seat seat) {
        return SeatMapResponse.SeatInfo.builder()
                .id(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .seatClass(seat.getSeatClass())
                .status(seat.getStatus())
                .available(seat.isAvailable())
                .build();
    }

    private void logSeatChange(Seat seat, String previousStatus, String newStatus, UUID passengerId, String reason) {
        SeatAuditLog auditLog = SeatAuditLog.builder()
                .seatId(seat.getId())
                .flightId(seat.getFlight().getId())
                .seatNumber(seat.getSeatNumber())
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedByPassengerId(passengerId)
                .changeReason(reason)
                .build();
        auditLogRepository.save(auditLog);
    }

    @CacheEvict(value = "seatMap", key = "#flightId")
    public void evictSeatMapCache(UUID flightId) {
        log.debug("Evicting seat map cache for flight: {}", flightId);
    }
}

