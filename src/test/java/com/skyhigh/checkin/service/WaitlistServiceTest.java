package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
import com.skyhigh.checkin.dto.event.SeatReleasedEvent;
import com.skyhigh.checkin.exception.*;
import com.skyhigh.checkin.model.entity.*;
import com.skyhigh.checkin.model.enums.SeatClass;
import com.skyhigh.checkin.model.enums.SeatStatus;
import com.skyhigh.checkin.model.enums.WaitlistStatus;
import com.skyhigh.checkin.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock private WaitlistRepository waitlistRepository;
    @Mock private FlightRepository flightRepository;
    @Mock private PassengerRepository passengerRepository;
    @Mock private SeatRepository seatRepository;
    @Mock private SeatEventPublisher seatEventPublisher;
    @Mock private CheckInConfig checkInConfig;

    private WaitlistService waitlistService;

    private UUID flightId;
    private UUID passengerId;
    private Flight flight;
    private Passenger passenger;
    private Seat seat;

    @BeforeEach
    void setUp() {
        waitlistService = new WaitlistService(waitlistRepository, flightRepository,
                passengerRepository, seatRepository, seatEventPublisher, checkInConfig,
                new SimpleMeterRegistry());

        flightId = UUID.randomUUID();
        passengerId = UUID.randomUUID();

        flight = Flight.builder().id(flightId).flightNumber("SH101").build();
        passenger = Passenger.builder().id(passengerId).firstName("John").lastName("Doe")
                .email("john@test.com").build();
        seat = Seat.builder().id(UUID.randomUUID()).flight(flight).seatNumber("1A")
                .seatClass(SeatClass.FIRST).status(SeatStatus.AVAILABLE).build();
    }

    @Nested
    @DisplayName("joinWaitlist")
    class JoinWaitlistTests {
        @Test
        @DisplayName("Should add passenger to waitlist with FIFO priority")
        void shouldJoinWaitlist() {
            when(flightRepository.findById(flightId)).thenReturn(Optional.of(flight));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
            when(waitlistRepository.findActiveByFlightAndPassenger(flightId, passengerId)).thenReturn(Optional.empty());
            when(waitlistRepository.countByFlightIdAndStatus(flightId, WaitlistStatus.WAITING)).thenReturn(0L);
            when(waitlistRepository.findMaxPriorityByFlightId(flightId)).thenReturn(0);
            when(checkInConfig.getWaitlistMaxPerFlight()).thenReturn(50);
            when(waitlistRepository.save(any(WaitlistEntry.class))).thenAnswer(i -> {
                WaitlistEntry e = i.getArgument(0);
                e.setId(UUID.randomUUID());
                return e;
            });

            WaitlistEntry entry = waitlistService.joinWaitlist(flightId, passengerId, SeatClass.ECONOMY);

            assertNotNull(entry);
            assertEquals(WaitlistStatus.WAITING, entry.getStatus());
            assertEquals(1, entry.getPriority());
        }

        @Test
        @DisplayName("Should throw when already on waitlist")
        void shouldThrowWhenAlreadyOnWaitlist() {
            WaitlistEntry existing = WaitlistEntry.builder().id(UUID.randomUUID()).build();
            when(flightRepository.findById(flightId)).thenReturn(Optional.of(flight));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
            when(waitlistRepository.findActiveByFlightAndPassenger(flightId, passengerId))
                    .thenReturn(Optional.of(existing));

            assertThrows(AlreadyOnWaitlistException.class,
                    () -> waitlistService.joinWaitlist(flightId, passengerId, null));
        }

        @Test
        @DisplayName("Should throw when waitlist is full")
        void shouldThrowWhenWaitlistFull() {
            when(flightRepository.findById(flightId)).thenReturn(Optional.of(flight));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
            when(waitlistRepository.findActiveByFlightAndPassenger(flightId, passengerId)).thenReturn(Optional.empty());
            when(waitlistRepository.countByFlightIdAndStatus(flightId, WaitlistStatus.WAITING)).thenReturn(50L);
            when(checkInConfig.getWaitlistMaxPerFlight()).thenReturn(50);

            assertThrows(WaitlistFullException.class,
                    () -> waitlistService.joinWaitlist(flightId, passengerId, null));
        }
    }

    @Nested
    @DisplayName("offerSeatToNextInLine")
    class OfferSeatTests {
        @Test
        @DisplayName("Should offer released seat to next waiting passenger")
        void shouldOfferSeatToNextWaiting() {
            WaitlistEntry entry = WaitlistEntry.builder()
                    .id(UUID.randomUUID()).flight(flight).passenger(passenger)
                    .status(WaitlistStatus.WAITING).priority(1).build();

            SeatReleasedEvent event = SeatReleasedEvent.builder()
                    .flightId(flightId).seatId(seat.getId()).seatNumber("1A")
                    .seatClass("FIRST").reason("CANCELLED").build();

            when(waitlistRepository.findNextWaitingByFlightAndClass(flightId, SeatClass.FIRST))
                    .thenReturn(List.of(entry));
            when(seatRepository.findById(seat.getId())).thenReturn(Optional.of(seat));
            when(checkInConfig.getWaitlistOfferDurationMinutes()).thenReturn(5);
            when(waitlistRepository.save(any(WaitlistEntry.class))).thenAnswer(i -> i.getArgument(0));

            waitlistService.offerSeatToNextInLine(event);

            assertEquals(WaitlistStatus.OFFERED, entry.getStatus());
            assertNotNull(entry.getOfferedAt());
            assertNotNull(entry.getOfferExpiresAt());
            verify(seatEventPublisher).publishWaitlistOffer(any());
        }

        @Test
        @DisplayName("Should do nothing when no waitlisted passengers")
        void shouldDoNothingWhenNoWaitlist() {
            SeatReleasedEvent event = SeatReleasedEvent.builder()
                    .flightId(flightId).seatId(seat.getId()).seatNumber("1A")
                    .seatClass("FIRST").reason("CANCELLED").build();

            when(waitlistRepository.findNextWaitingByFlightAndClass(flightId, SeatClass.FIRST))
                    .thenReturn(Collections.emptyList());
            when(waitlistRepository.findByFlightIdAndStatusOrderByPriority(flightId, WaitlistStatus.WAITING))
                    .thenReturn(Collections.emptyList());

            waitlistService.offerSeatToNextInLine(event);

            verify(waitlistRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("acceptOffer")
    class AcceptOfferTests {
        @Test
        @DisplayName("Should accept a valid offer")
        void shouldAcceptValidOffer() {
            UUID entryId = UUID.randomUUID();
            WaitlistEntry entry = WaitlistEntry.builder()
                    .id(entryId).flight(flight).passenger(passenger).assignedSeat(seat)
                    .status(WaitlistStatus.OFFERED)
                    .offerExpiresAt(LocalDateTime.now().plusMinutes(5)).build();

            when(waitlistRepository.findById(entryId)).thenReturn(Optional.of(entry));
            when(waitlistRepository.save(any(WaitlistEntry.class))).thenAnswer(i -> i.getArgument(0));

            WaitlistEntry result = waitlistService.acceptOffer(entryId, passengerId);

            assertEquals(WaitlistStatus.ASSIGNED, result.getStatus());
            assertNotNull(result.getAssignedAt());
        }

        @Test
        @DisplayName("Should throw when offer has expired")
        void shouldThrowWhenOfferExpired() {
            UUID entryId = UUID.randomUUID();
            WaitlistEntry entry = WaitlistEntry.builder()
                    .id(entryId).flight(flight).passenger(passenger).assignedSeat(seat)
                    .status(WaitlistStatus.OFFERED)
                    .offerExpiresAt(LocalDateTime.now().minusMinutes(1)).build();

            when(waitlistRepository.findById(entryId)).thenReturn(Optional.of(entry));
            when(waitlistRepository.save(any(WaitlistEntry.class))).thenAnswer(i -> i.getArgument(0));

            assertThrows(WaitlistOfferExpiredException.class,
                    () -> waitlistService.acceptOffer(entryId, passengerId));
        }
    }

    @Nested
    @DisplayName("declineOffer")
    class DeclineOfferTests {
        @Test
        @DisplayName("Should decline offer and re-publish seat released event")
        void shouldDeclineAndReOffer() {
            UUID entryId = UUID.randomUUID();
            WaitlistEntry entry = WaitlistEntry.builder()
                    .id(entryId).flight(flight).passenger(passenger).assignedSeat(seat)
                    .status(WaitlistStatus.OFFERED)
                    .offerExpiresAt(LocalDateTime.now().plusMinutes(5)).build();

            when(waitlistRepository.findById(entryId)).thenReturn(Optional.of(entry));
            when(waitlistRepository.save(any(WaitlistEntry.class))).thenAnswer(i -> i.getArgument(0));

            waitlistService.declineOffer(entryId, passengerId);

            assertEquals(WaitlistStatus.LEFT, entry.getStatus());
            verify(seatEventPublisher).publishSeatReleased(any());
        }
    }

    @Nested
    @DisplayName("leaveWaitlist")
    class LeaveWaitlistTests {
        @Test
        @DisplayName("Should leave waitlist successfully")
        void shouldLeaveWaitlist() {
            WaitlistEntry entry = WaitlistEntry.builder()
                    .id(UUID.randomUUID()).flight(flight).passenger(passenger)
                    .status(WaitlistStatus.WAITING).build();

            when(waitlistRepository.findActiveByFlightAndPassenger(flightId, passengerId))
                    .thenReturn(Optional.of(entry));
            when(waitlistRepository.save(any(WaitlistEntry.class))).thenAnswer(i -> i.getArgument(0));

            waitlistService.leaveWaitlist(flightId, passengerId);

            assertEquals(WaitlistStatus.LEFT, entry.getStatus());
        }
    }
}

