package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
import com.skyhigh.checkin.dto.event.SeatReleasedEvent;
import com.skyhigh.checkin.dto.event.WaitlistOfferEvent;
import com.skyhigh.checkin.exception.*;
import com.skyhigh.checkin.model.entity.*;
import com.skyhigh.checkin.model.enums.SeatClass;
import com.skyhigh.checkin.model.enums.SeatStatus;
import com.skyhigh.checkin.model.enums.WaitlistStatus;
import com.skyhigh.checkin.repository.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final FlightRepository flightRepository;
    private final PassengerRepository passengerRepository;
    private final SeatRepository seatRepository;
    private final SeatEventPublisher seatEventPublisher;
    private final CheckInConfig checkInConfig;
    private final Counter waitlistJoinCounter;
    private final Counter waitlistOfferCounter;
    private final Counter waitlistAssignCounter;

    public WaitlistService(WaitlistRepository waitlistRepository,
                           FlightRepository flightRepository,
                           PassengerRepository passengerRepository,
                           SeatRepository seatRepository,
                           SeatEventPublisher seatEventPublisher,
                           CheckInConfig checkInConfig,
                           MeterRegistry meterRegistry) {
        this.waitlistRepository = waitlistRepository;
        this.flightRepository = flightRepository;
        this.passengerRepository = passengerRepository;
        this.seatRepository = seatRepository;
        this.seatEventPublisher = seatEventPublisher;
        this.checkInConfig = checkInConfig;
        this.waitlistJoinCounter = Counter.builder("skyhigh.waitlist.joins")
                .description("Number of waitlist joins").register(meterRegistry);
        this.waitlistOfferCounter = Counter.builder("skyhigh.waitlist.offers")
                .description("Number of waitlist offers made").register(meterRegistry);
        this.waitlistAssignCounter = Counter.builder("skyhigh.waitlist.assignments")
                .description("Number of waitlist assignments completed").register(meterRegistry);
    }

    /**
     * Join the waitlist for a flight. FIFO ordering by auto-incrementing priority.
     */
    @Transactional
    public WaitlistEntry joinWaitlist(UUID flightId, UUID passengerId, SeatClass preferredSeatClass) {
        log.info("Passenger {} joining waitlist for flight {} (preferred class: {})",
                passengerId, flightId, preferredSeatClass);

        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", flightId));

        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger", passengerId));

        // Check if already on waitlist
        waitlistRepository.findActiveByFlightAndPassenger(flightId, passengerId)
                .ifPresent(existing -> {
                    throw new AlreadyOnWaitlistException(flightId, passengerId);
                });

        // Check waitlist capacity
        long currentCount = waitlistRepository.countByFlightIdAndStatus(flightId, WaitlistStatus.WAITING);
        if (currentCount >= checkInConfig.getWaitlistMaxPerFlight()) {
            throw new WaitlistFullException(checkInConfig.getWaitlistMaxPerFlight());
        }

        // Get next priority number (FIFO)
        int nextPriority = waitlistRepository.findMaxPriorityByFlightId(flightId) + 1;

        WaitlistEntry entry = WaitlistEntry.builder()
                .flight(flight)
                .passenger(passenger)
                .preferredSeatClass(preferredSeatClass)
                .priority(nextPriority)
                .status(WaitlistStatus.WAITING)
                .joinedAt(LocalDateTime.now())
                .build();

        entry = waitlistRepository.save(entry);
        waitlistJoinCounter.increment();

        log.info("Passenger {} added to waitlist for flight {} at position {}",
                passengerId, flightId, nextPriority);

        return entry;
    }

    /**
     * Leave the waitlist voluntarily.
     */
    @Transactional
    public void leaveWaitlist(UUID flightId, UUID passengerId) {
        log.info("Passenger {} leaving waitlist for flight {}", passengerId, flightId);

        WaitlistEntry entry = waitlistRepository.findActiveByFlightAndPassenger(flightId, passengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist entry",
                        "passenger " + passengerId + " on flight " + flightId));

        entry.setStatus(WaitlistStatus.LEFT);
        waitlistRepository.save(entry);

        log.info("Passenger {} removed from waitlist for flight {}", passengerId, flightId);
    }

    /**
     * Get waitlist status for a passenger on a flight.
     */
    @Transactional(readOnly = true)
    public WaitlistStatusInfo getWaitlistStatus(UUID flightId, UUID passengerId) {
        WaitlistEntry entry = waitlistRepository.findActiveByFlightAndPassenger(flightId, passengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist entry",
                        "passenger " + passengerId + " on flight " + flightId));

        long positionAhead = waitlistRepository.countAheadInQueue(flightId, entry.getPriority());

        return new WaitlistStatusInfo(
                entry.getId(),
                entry.getStatus(),
                (int) positionAhead + 1,
                waitlistRepository.countByFlightIdAndStatus(flightId, WaitlistStatus.WAITING),
                entry.getPreferredSeatClass(),
                entry.getJoinedAt(),
                entry.getOfferedAt(),
                entry.getOfferExpiresAt()
        );
    }

    /**
     * Get all waitlist entries for a flight.
     */
    @Transactional(readOnly = true)
    public List<WaitlistEntry> getWaitlistForFlight(UUID flightId) {
        return waitlistRepository.findByFlightIdAndStatusOrderByPriority(flightId, WaitlistStatus.WAITING);
    }

    /**
     * Process a seat released event — offer the seat to the next eligible waitlisted passenger.
     * Called by the RabbitMQ listener when a seat becomes available.
     */
    @Transactional
    public void offerSeatToNextInLine(SeatReleasedEvent event) {
        log.info("Processing seat released event: flight={}, seat={}, class={}",
                event.getFlightId(), event.getSeatNumber(), event.getSeatClass());

        SeatClass seatClass = SeatClass.valueOf(event.getSeatClass());

        // Find the next waiting passenger (preferring those who want this seat class)
        List<WaitlistEntry> candidates = waitlistRepository
                .findNextWaitingByFlightAndClass(event.getFlightId(), seatClass);

        if (candidates.isEmpty()) {
            // Try any waiting passenger regardless of preference
            candidates = waitlistRepository
                    .findByFlightIdAndStatusOrderByPriority(event.getFlightId(), WaitlistStatus.WAITING);
        }

        if (candidates.isEmpty()) {
            log.info("No waitlisted passengers for flight {} seat class {}",
                    event.getFlightId(), event.getSeatClass());
            return;
        }

        WaitlistEntry nextInLine = candidates.get(0);
        Seat seat = seatRepository.findById(event.getSeatId())
                .orElse(null);

        if (seat == null || !seat.isAvailable()) {
            log.warn("Seat {} is no longer available", event.getSeatId());
            return;
        }

        // Offer the seat
        LocalDateTime offerExpires = LocalDateTime.now()
                .plusMinutes(checkInConfig.getWaitlistOfferDurationMinutes());

        nextInLine.setStatus(WaitlistStatus.OFFERED);
        nextInLine.setOfferedAt(LocalDateTime.now());
        nextInLine.setOfferExpiresAt(offerExpires);
        nextInLine.setAssignedSeat(seat);
        waitlistRepository.save(nextInLine);
        waitlistOfferCounter.increment();

        // Publish notification event
        Passenger passenger = nextInLine.getPassenger();
        WaitlistOfferEvent offerEvent = WaitlistOfferEvent.builder()
                .waitlistEntryId(nextInLine.getId())
                .flightId(event.getFlightId())
                .passengerId(passenger.getId())
                .seatId(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .seatClass(seat.getSeatClass().name())
                .offerExpiresAt(offerExpires)
                .passengerEmail(passenger.getEmail())
                .passengerName(passenger.getFirstName() + " " + passenger.getLastName())
                .build();

        seatEventPublisher.publishWaitlistOffer(offerEvent);

        log.info("Seat {} offered to waitlisted passenger {} (expires: {})",
                seat.getSeatNumber(), passenger.getId(), offerExpires);
    }

    /**
     * Accept a waitlist seat offer. Holds the seat for the passenger.
     */
    @Transactional
    public WaitlistEntry acceptOffer(UUID waitlistEntryId, UUID passengerId) {
        log.info("Passenger {} accepting waitlist offer {}", passengerId, waitlistEntryId);

        WaitlistEntry entry = waitlistRepository.findById(waitlistEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist entry", waitlistEntryId));

        if (!entry.getPassenger().getId().equals(passengerId)) {
            throw new FlightAccessDeniedException(passengerId, entry.getFlight().getId());
        }

        if (!entry.isOffered()) {
            throw new InvalidSeatStateException("Waitlist entry is not in OFFERED state");
        }

        if (entry.isOfferExpired()) {
            entry.setStatus(WaitlistStatus.EXPIRED);
            waitlistRepository.save(entry);
            throw new WaitlistOfferExpiredException();
        }

        // Verify seat is still available
        Seat seat = entry.getAssignedSeat();
        if (seat == null || !seat.isAvailable()) {
            entry.setStatus(WaitlistStatus.WAITING);
            entry.setAssignedSeat(null);
            entry.setOfferedAt(null);
            entry.setOfferExpiresAt(null);
            waitlistRepository.save(entry);
            throw new InvalidSeatStateException("Offered seat is no longer available. You remain on the waitlist.");
        }

        // Actually hold the seat for the passenger (prevents race condition with regular seat selection)
        seat.setStatus(SeatStatus.HELD);
        seat.setHeldByPassenger(entry.getPassenger());
        seat.setHeldUntil(LocalDateTime.now().plusSeconds(300)); // 5 min to complete check-in
        seatRepository.save(seat);

        // Mark as assigned
        entry.setStatus(WaitlistStatus.ASSIGNED);
        entry.setAssignedAt(LocalDateTime.now());
        entry.setNotificationSent(true);
        entry = waitlistRepository.save(entry);
        waitlistAssignCounter.increment();

        log.info("Waitlist offer accepted: passenger {} assigned seat {}",
                passengerId, seat.getSeatNumber());

        return entry;
    }

    /**
     * Decline a waitlist seat offer. The seat is offered to the next passenger.
     */
    @Transactional
    public void declineOffer(UUID waitlistEntryId, UUID passengerId) {
        log.info("Passenger {} declining waitlist offer {}", passengerId, waitlistEntryId);

        WaitlistEntry entry = waitlistRepository.findById(waitlistEntryId)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist entry", waitlistEntryId));

        if (!entry.getPassenger().getId().equals(passengerId)) {
            throw new FlightAccessDeniedException(passengerId, entry.getFlight().getId());
        }

        if (!entry.isOffered()) {
            throw new InvalidSeatStateException("Waitlist entry is not in OFFERED state");
        }

        Seat declinedSeat = entry.getAssignedSeat();

        // Move passenger back to waiting or remove
        entry.setStatus(WaitlistStatus.LEFT);
        entry.setAssignedSeat(null);
        waitlistRepository.save(entry);

        // Re-offer the seat to the next person
        if (declinedSeat != null && declinedSeat.isAvailable()) {
            SeatReleasedEvent reOfferEvent = SeatReleasedEvent.builder()
                    .flightId(entry.getFlight().getId())
                    .seatId(declinedSeat.getId())
                    .seatNumber(declinedSeat.getSeatNumber())
                    .seatClass(declinedSeat.getSeatClass().name())
                    .reason("WAITLIST_OFFER_DECLINED")
                    .releasedAt(LocalDateTime.now())
                    .build();
            seatEventPublisher.publishSeatReleased(reOfferEvent);
        }

        log.info("Waitlist offer declined by passenger {}", passengerId);
    }

    /**
     * Expire unclaimed offers and re-offer seats.
     */
    @Transactional
    public void expireUnclaimedOffers() {
        List<WaitlistEntry> expiredOffers = waitlistRepository.findExpiredOffers(LocalDateTime.now());

        for (WaitlistEntry entry : expiredOffers) {
            log.info("Expiring unclaimed waitlist offer: {} for passenger {}",
                    entry.getId(), entry.getPassenger().getId());

            Seat offeredSeat = entry.getAssignedSeat();
            entry.setStatus(WaitlistStatus.EXPIRED);
            entry.setAssignedSeat(null);
            waitlistRepository.save(entry);

            // Re-offer the seat to the next person
            if (offeredSeat != null && offeredSeat.isAvailable()) {
                SeatReleasedEvent reOfferEvent = SeatReleasedEvent.builder()
                        .flightId(entry.getFlight().getId())
                        .seatId(offeredSeat.getId())
                        .seatNumber(offeredSeat.getSeatNumber())
                        .seatClass(offeredSeat.getSeatClass().name())
                        .reason("WAITLIST_OFFER_EXPIRED")
                        .releasedAt(LocalDateTime.now())
                        .build();
                seatEventPublisher.publishSeatReleased(reOfferEvent);
            }
        }

        if (!expiredOffers.isEmpty()) {
            log.info("Expired {} unclaimed waitlist offers", expiredOffers.size());
        }
    }

    public record WaitlistStatusInfo(
            UUID entryId,
            WaitlistStatus status,
            int position,
            long totalWaiting,
            SeatClass preferredSeatClass,
            LocalDateTime joinedAt,
            LocalDateTime offeredAt,
            LocalDateTime offerExpiresAt
    ) {}
}

