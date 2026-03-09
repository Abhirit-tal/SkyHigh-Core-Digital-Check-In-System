package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
import com.skyhigh.checkin.dto.response.SeatHoldResponse;
import com.skyhigh.checkin.dto.response.SeatMapResponse;
import com.skyhigh.checkin.exception.*;
import com.skyhigh.checkin.model.entity.*;
import com.skyhigh.checkin.model.enums.CheckInStatus;
import com.skyhigh.checkin.model.enums.FlightStatus;
import com.skyhigh.checkin.model.enums.SeatClass;
import com.skyhigh.checkin.model.enums.SeatStatus;
import com.skyhigh.checkin.repository.CheckInRepository;
import com.skyhigh.checkin.repository.PassengerRepository;
import com.skyhigh.checkin.repository.SeatAuditLogRepository;
import com.skyhigh.checkin.repository.SeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock private SeatRepository seatRepository;
    @Mock private PassengerRepository passengerRepository;
    @Mock private CheckInRepository checkInRepository;
    @Mock private SeatAuditLogRepository auditLogRepository;
    @Mock private SeatLockService seatLockService;
    @Mock private SeatEventPublisher seatEventPublisher;
    @Mock private SeatDomainEventPublisher seatDomainEventPublisher;
    @Mock private CheckInConfig checkInConfig;

    private SeatService seatService;
    private MeterRegistry meterRegistry;

    private UUID flightId;
    private UUID passengerId;
    private UUID seatId;
    private UUID checkInId;
    private Flight flight;
    private Passenger passenger;
    private Seat seat;
    private CheckIn checkIn;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        seatService = new SeatService(seatRepository, passengerRepository, checkInRepository,
                auditLogRepository, seatLockService, seatEventPublisher, seatDomainEventPublisher, checkInConfig, meterRegistry);

        flightId = UUID.randomUUID();
        passengerId = UUID.randomUUID();
        seatId = UUID.randomUUID();
        checkInId = UUID.randomUUID();

        flight = Flight.builder()
                .id(flightId).flightNumber("SH101")
                .departureTime(LocalDateTime.now().plusHours(20))
                .arrivalTime(LocalDateTime.now().plusHours(22))
                .origin("DEL").destination("BOM")
                .status(FlightStatus.SCHEDULED).totalSeats(180).build();

        passenger = Passenger.builder()
                .id(passengerId).firstName("John").lastName("Doe")
                .email("john@example.com").build();

        seat = Seat.builder()
                .id(seatId).flight(flight).seatNumber("1A")
                .seatClass(SeatClass.FIRST).status(SeatStatus.AVAILABLE).version(0).build();

        checkIn = CheckIn.builder()
                .id(checkInId).status(CheckInStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .lastActivityAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build();
    }

    @Nested
    @DisplayName("getSeatMap")
    class GetSeatMapTests {
        @Test
        @DisplayName("Should return seat map grouped by class")
        void shouldReturnSeatMap() {
            Seat seat2 = Seat.builder().id(UUID.randomUUID()).flight(flight).seatNumber("10A")
                    .seatClass(SeatClass.ECONOMY).status(SeatStatus.AVAILABLE).build();

            when(seatRepository.findByFlightIdOrderBySeatClassAndNumber(flightId))
                    .thenReturn(Arrays.asList(seat, seat2));

            SeatMapResponse response = seatService.getSeatMap(flightId);

            assertNotNull(response);
            assertEquals(flightId, response.getFlightId());
            assertEquals(2, response.getSummary().getTotal());
            assertEquals(2, response.getSummary().getAvailable());
        }

        @Test
        @DisplayName("Should throw when no seats found")
        void shouldThrowWhenNoSeats() {
            when(seatRepository.findByFlightIdOrderBySeatClassAndNumber(flightId))
                    .thenReturn(Collections.emptyList());

            assertThrows(ResourceNotFoundException.class, () -> seatService.getSeatMap(flightId));
        }
    }

    @Nested
    @DisplayName("holdSeat")
    class HoldSeatTests {
        @Test
        @DisplayName("Should hold an available seat successfully")
        void shouldHoldAvailableSeat() {
            when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
            when(checkInRepository.findById(checkInId)).thenReturn(Optional.of(checkIn));
            when(seatLockService.acquireLock(any(), any(), any(), anyInt())).thenReturn(true);
            when(checkInConfig.getSeatHoldDurationSeconds()).thenReturn(120);
            when(seatRepository.save(any(Seat.class))).thenAnswer(i -> i.getArgument(0));
            when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));
            when(auditLogRepository.save(any())).thenReturn(null);

            SeatHoldResponse response = seatService.holdSeat(seatId, passengerId, checkInId);

            assertNotNull(response);
            assertEquals(SeatStatus.HELD, response.getStatus());
            assertEquals(120, response.getHoldDurationSeconds());
            verify(seatLockService).acquireLock(flightId, "1A", passengerId, 120);
        }

        @Test
        @DisplayName("Should throw 409 when seat is already held by another passenger")
        void shouldThrow409WhenSeatHeld() {
            Passenger otherPassenger = Passenger.builder().id(UUID.randomUUID()).build();
            seat.setStatus(SeatStatus.HELD);
            seat.setHeldByPassenger(otherPassenger);
            seat.setHeldUntil(LocalDateTime.now().plusMinutes(1));

            when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
            when(checkInRepository.findById(checkInId)).thenReturn(Optional.of(checkIn));

            assertThrows(SeatAlreadyHeldException.class,
                    () -> seatService.holdSeat(seatId, passengerId, checkInId));
        }

        @Test
        @DisplayName("Should throw when seat is already confirmed")
        void shouldThrowWhenSeatConfirmed() {
            seat.setStatus(SeatStatus.CONFIRMED);

            when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
            when(checkInRepository.findById(checkInId)).thenReturn(Optional.of(checkIn));

            assertThrows(SeatAlreadyConfirmedException.class,
                    () -> seatService.holdSeat(seatId, passengerId, checkInId));
        }

        @Test
        @DisplayName("Should throw when Redis lock cannot be acquired")
        void shouldThrowWhenRedisLockFails() {
            when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
            when(checkInRepository.findById(checkInId)).thenReturn(Optional.of(checkIn));
            when(seatLockService.acquireLock(any(), any(), any(), anyInt())).thenReturn(false);
            when(seatLockService.isLockedByPassenger(any(), any(), any())).thenReturn(false);
            when(checkInConfig.getSeatHoldDurationSeconds()).thenReturn(120);

            assertThrows(SeatAlreadyHeldException.class,
                    () -> seatService.holdSeat(seatId, passengerId, checkInId));
        }
    }

    @Nested
    @DisplayName("confirmSeat")
    class ConfirmSeatTests {
        @Test
        @DisplayName("Should confirm a held seat")
        void shouldConfirmHeldSeat() {
            seat.setStatus(SeatStatus.HELD);
            seat.setHeldByPassenger(passenger);
            seat.setHeldUntil(LocalDateTime.now().plusMinutes(1));

            when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
            when(seatRepository.save(any(Seat.class))).thenAnswer(i -> i.getArgument(0));
            when(auditLogRepository.save(any())).thenReturn(null);

            Seat result = seatService.confirmSeat(seatId, passengerId);

            assertEquals(SeatStatus.CONFIRMED, result.getStatus());
            verify(seatLockService).forceReleaseLock(flightId, "1A");
        }

        @Test
        @DisplayName("Should throw when hold is expired")
        void shouldThrowWhenHoldExpired() {
            seat.setStatus(SeatStatus.HELD);
            seat.setHeldByPassenger(passenger);
            seat.setHeldUntil(LocalDateTime.now().minusMinutes(1)); // expired

            when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
            when(seatRepository.save(any(Seat.class))).thenAnswer(i -> i.getArgument(0));

            assertThrows(SeatHoldExpiredException.class,
                    () -> seatService.confirmSeat(seatId, passengerId));
        }

        @Test
        @DisplayName("Should throw when seat not held by requesting passenger")
        void shouldThrowWhenNotHeldByPassenger() {
            Passenger otherPassenger = Passenger.builder().id(UUID.randomUUID()).build();
            seat.setStatus(SeatStatus.HELD);
            seat.setHeldByPassenger(otherPassenger);
            seat.setHeldUntil(LocalDateTime.now().plusMinutes(1));

            when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));

            assertThrows(InvalidSeatStateException.class,
                    () -> seatService.confirmSeat(seatId, passengerId));
        }

        @Test
        @DisplayName("Should handle optimistic lock failure on confirm")
        void shouldHandleOptimisticLockFailure() {
            seat.setStatus(SeatStatus.HELD);
            seat.setHeldByPassenger(passenger);
            seat.setHeldUntil(LocalDateTime.now().plusMinutes(1));

            when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
            when(seatRepository.save(any(Seat.class)))
                    .thenThrow(new ObjectOptimisticLockingFailureException("Seat", seatId));

            assertThrows(InvalidSeatStateException.class,
                    () -> seatService.confirmSeat(seatId, passengerId));
        }
    }

    @Nested
    @DisplayName("cancelConfirmedSeat")
    class CancelConfirmedSeatTests {
        @Test
        @DisplayName("Should cancel a confirmed seat and publish event")
        void shouldCancelConfirmedSeat() {
            seat.setStatus(SeatStatus.CONFIRMED);
            seat.setConfirmedByPassenger(passenger);

            when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));
            when(seatRepository.save(any(Seat.class))).thenAnswer(i -> i.getArgument(0));
            when(auditLogRepository.save(any())).thenReturn(null);

            Seat result = seatService.cancelConfirmedSeat(seatId, passengerId);

            assertEquals(SeatStatus.AVAILABLE, result.getStatus());
            assertNull(result.getConfirmedByPassenger());
            verify(seatDomainEventPublisher).publishAfterCommit(any());
            verify(auditLogRepository, times(2)).save(any()); // CANCELLED + AVAILABLE
        }

        @Test
        @DisplayName("Should throw when seat is not confirmed")
        void shouldThrowWhenSeatNotConfirmed() {
            seat.setStatus(SeatStatus.AVAILABLE);

            when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));

            assertThrows(InvalidSeatStateException.class,
                    () -> seatService.cancelConfirmedSeat(seatId, passengerId));
        }

        @Test
        @DisplayName("Should throw when seat not confirmed by requesting passenger")
        void shouldThrowWhenNotConfirmedByPassenger() {
            Passenger otherPassenger = Passenger.builder().id(UUID.randomUUID()).build();
            seat.setStatus(SeatStatus.CONFIRMED);
            seat.setConfirmedByPassenger(otherPassenger);

            when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
            when(passengerRepository.findById(passengerId)).thenReturn(Optional.of(passenger));

            assertThrows(InvalidSeatStateException.class,
                    () -> seatService.cancelConfirmedSeat(seatId, passengerId));
        }
    }

    @Nested
    @DisplayName("releaseSeatHold")
    class ReleaseSeatHoldTests {
        @Test
        @DisplayName("Should release a held seat")
        void shouldReleaseHeldSeat() {
            seat.setStatus(SeatStatus.HELD);
            seat.setHeldByPassenger(passenger);

            when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));
            when(seatRepository.save(any(Seat.class))).thenAnswer(i -> i.getArgument(0));
            when(auditLogRepository.save(any())).thenReturn(null);

            seatService.releaseSeatHold(seatId, passengerId);

            verify(seatLockService).releaseLock(flightId, "1A", passengerId);
            verify(seatRepository).save(any(Seat.class));
        }

        @Test
        @DisplayName("Should throw when trying to release seat held by another passenger")
        void shouldThrowWhenReleasingOthersSeat() {
            Passenger otherPassenger = Passenger.builder().id(UUID.randomUUID()).build();
            seat.setStatus(SeatStatus.HELD);
            seat.setHeldByPassenger(otherPassenger);

            when(seatRepository.findByIdWithLock(seatId)).thenReturn(Optional.of(seat));

            assertThrows(InvalidSeatStateException.class,
                    () -> seatService.releaseSeatHold(seatId, passengerId));
        }
    }
}

