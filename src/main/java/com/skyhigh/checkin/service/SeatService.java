package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
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
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@Slf4j
public class SeatService {

    private final SeatRepository seatRepository;
    private final PassengerRepository passengerRepository;
    private final CheckInRepository checkInRepository;
    private final SeatAuditLogRepository auditLogRepository;
    private final SeatLockService seatLockService;
    private final CheckInConfig checkInConfig;

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
            return seat;
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic lock failure while confirming seat {}", seatId);
            throw new InvalidSeatStateException("Seat was modified by another process. Please try again.");
        }
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

