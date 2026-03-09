package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
import com.skyhigh.checkin.dto.request.BaggageRequest;
import com.skyhigh.checkin.dto.request.PaymentRequest;
import com.skyhigh.checkin.dto.request.StartCheckInRequest;
import com.skyhigh.checkin.dto.response.CheckInResponse;
import com.skyhigh.checkin.exception.*;
import com.skyhigh.checkin.model.entity.*;
import com.skyhigh.checkin.model.enums.*;
import com.skyhigh.checkin.repository.BookingRepository;
import com.skyhigh.checkin.repository.CheckInRepository;
import com.skyhigh.checkin.security.PassengerPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckInServiceTest {

    @Mock private CheckInRepository checkInRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private SeatService seatService;
    @Mock private WeightService weightService;
    @Mock private PaymentService paymentService;
    @Mock private BoardingPassService boardingPassService;
    @Mock private CheckInConfig checkInConfig;

    @InjectMocks
    private CheckInService checkInService;

    private PassengerPrincipal principal;
    private UUID passengerId;
    private UUID flightId;
    private UUID checkInId;
    private Flight flight;
    private Passenger passenger;
    private Booking booking;
    private CheckIn checkIn;

    @BeforeEach
    void setUp() {
        passengerId = UUID.randomUUID();
        flightId = UUID.randomUUID();
        checkInId = UUID.randomUUID();

        principal = new PassengerPrincipal(passengerId, "john@test.com", "John", "Doe",
                List.of(flightId));

        flight = Flight.builder().id(flightId).flightNumber("SH101")
                .departureTime(LocalDateTime.now().plusHours(20))
                .origin("DEL").destination("BOM").gate("A1").build();

        passenger = Passenger.builder().id(passengerId).firstName("John").lastName("Doe")
                .email("john@test.com").build();

        booking = Booking.builder().id(UUID.randomUUID()).flight(flight).passenger(passenger)
                .bookingReference("BK001").status(BookingStatus.ACTIVE).build();

        checkIn = CheckIn.builder().id(checkInId).booking(booking)
                .status(CheckInStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .lastActivityAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
    }

    @Nested
    @DisplayName("startCheckIn")
    class StartCheckInTests {
        @Test
        @DisplayName("Should start check-in within valid window")
        void shouldStartCheckIn() {
            StartCheckInRequest request = new StartCheckInRequest();
            request.setFlightId(flightId);

            when(bookingRepository.findActiveBookingByPassengerAndFlight(passengerId, flightId))
                    .thenReturn(Optional.of(booking));
            when(checkInConfig.getCheckinWindowOpensHours()).thenReturn(24);
            when(checkInConfig.getCheckinWindowClosesHours()).thenReturn(1);
            when(checkInConfig.getSessionTimeoutMinutes()).thenReturn(10);
            when(checkInRepository.findActiveCheckInByBookingId(any())).thenReturn(Optional.empty());
            when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> {
                CheckIn ci = i.getArgument(0);
                ci.setId(checkInId);
                return ci;
            });

            CheckInResponse response = checkInService.startCheckIn(request, principal);

            assertNotNull(response);
            assertEquals(CheckInStatus.IN_PROGRESS, response.getStatus());
        }

        @Test
        @DisplayName("Should throw when check-in already exists")
        void shouldThrowWhenCheckInExists() {
            StartCheckInRequest request = new StartCheckInRequest();
            request.setFlightId(flightId);

            when(bookingRepository.findActiveBookingByPassengerAndFlight(passengerId, flightId))
                    .thenReturn(Optional.of(booking));
            when(checkInConfig.getCheckinWindowOpensHours()).thenReturn(24);
            when(checkInConfig.getCheckinWindowClosesHours()).thenReturn(1);
            when(checkInRepository.findActiveCheckInByBookingId(any()))
                    .thenReturn(Optional.of(checkIn));

            assertThrows(CheckInAlreadyExistsException.class,
                    () -> checkInService.startCheckIn(request, principal));
        }
    }

    @Nested
    @DisplayName("addBaggage")
    class AddBaggageTests {
        @Test
        @DisplayName("Should add baggage within weight limit")
        void shouldAddBaggageWithinLimit() {
            when(checkInRepository.findByIdWithDetails(checkInId)).thenReturn(Optional.of(checkIn));
            when(weightService.validateWeight(any())).thenReturn(
                    new WeightService.WeightValidationResult(
                            BigDecimal.valueOf(20), BigDecimal.valueOf(25), BigDecimal.ZERO,
                            BigDecimal.ZERO, "INR", BigDecimal.valueOf(200), true));
            when(checkInConfig.getSessionTimeoutMinutes()).thenReturn(10);
            when(checkInConfig.getMaxBaggageWeightKg()).thenReturn(BigDecimal.valueOf(25));
            when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));

            BaggageRequest request = new BaggageRequest();
            request.setWeightKg(BigDecimal.valueOf(20));

            CheckInResponse response = checkInService.addBaggage(checkInId, request, principal);

            assertNotNull(response);
            assertEquals(CheckInStatus.IN_PROGRESS, response.getStatus());
        }

        @Test
        @DisplayName("Should require payment for excess baggage")
        void shouldRequirePaymentForExcessBaggage() {
            when(checkInRepository.findByIdWithDetails(checkInId)).thenReturn(Optional.of(checkIn));
            when(weightService.validateWeight(any())).thenReturn(
                    new WeightService.WeightValidationResult(
                            BigDecimal.valueOf(30), BigDecimal.valueOf(25), BigDecimal.valueOf(5),
                            BigDecimal.valueOf(1000), "INR", BigDecimal.valueOf(200), false));
            when(checkInConfig.getSessionTimeoutMinutes()).thenReturn(10);
            when(checkInConfig.getMaxBaggageWeightKg()).thenReturn(BigDecimal.valueOf(25));
            when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));

            BaggageRequest request = new BaggageRequest();
            request.setWeightKg(BigDecimal.valueOf(30));

            CheckInResponse response = checkInService.addBaggage(checkInId, request, principal);

            assertNotNull(response);
            assertEquals(CheckInStatus.WAITING_PAYMENT, response.getStatus());
        }
    }

    @Nested
    @DisplayName("processPayment")
    class ProcessPaymentTests {
        @Test
        @DisplayName("Should process payment successfully")
        void shouldProcessPayment() {
            checkIn.setStatus(CheckInStatus.WAITING_PAYMENT);
            checkIn.setExcessBaggageFee(BigDecimal.valueOf(1000));

            when(checkInRepository.findByIdWithDetails(checkInId)).thenReturn(Optional.of(checkIn));
            when(checkInConfig.getSessionTimeoutMinutes()).thenReturn(10);
            when(paymentService.processPayment(any(), any(), any())).thenReturn(
                    new PaymentService.PaymentResult(UUID.randomUUID(), "COMPLETED",
                            BigDecimal.valueOf(1000), "INR", "PAY-123", null, LocalDateTime.now()));
            when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));

            PaymentRequest request = new PaymentRequest();
            request.setAmount(BigDecimal.valueOf(1000));
            request.setCurrency("INR");

            CheckInResponse response = checkInService.processPayment(checkInId, request, principal);

            assertNotNull(response);
            assertEquals(CheckInStatus.IN_PROGRESS, response.getStatus());
        }

        @Test
        @DisplayName("Should throw when not waiting for payment")
        void shouldThrowWhenNotWaitingPayment() {
            when(checkInRepository.findByIdWithDetails(checkInId)).thenReturn(Optional.of(checkIn));

            PaymentRequest request = new PaymentRequest();
            request.setAmount(BigDecimal.valueOf(1000));

            assertThrows(InvalidSeatStateException.class,
                    () -> checkInService.processPayment(checkInId, request, principal));
        }
    }

    @Nested
    @DisplayName("cancelCheckIn")
    class CancelCheckInTests {
        @Test
        @DisplayName("Should cancel in-progress check-in and release held seat")
        void shouldCancelInProgressCheckIn() {
            Seat heldSeat = Seat.builder().id(UUID.randomUUID()).flight(flight)
                    .seatNumber("1A").status(SeatStatus.HELD)
                    .heldByPassenger(passenger).build();
            checkIn.setSeat(heldSeat);

            when(checkInRepository.findByIdWithDetails(checkInId)).thenReturn(Optional.of(checkIn));
            when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));

            checkInService.cancelCheckIn(checkInId, principal);

            verify(seatService).releaseSeatHold(heldSeat.getId(), passengerId);
            verify(checkInRepository).save(argThat(ci -> ci.getStatus() == CheckInStatus.CANCELLED));
        }

        @Test
        @DisplayName("Should cancel completed check-in and cancel confirmed seat")
        void shouldCancelCompletedCheckIn() {
            Seat confirmedSeat = Seat.builder().id(UUID.randomUUID()).flight(flight)
                    .seatNumber("1A").status(SeatStatus.CONFIRMED)
                    .confirmedByPassenger(passenger).build();
            checkIn.setStatus(CheckInStatus.COMPLETED);
            checkIn.setSeat(confirmedSeat);

            when(checkInRepository.findByIdWithDetails(checkInId)).thenReturn(Optional.of(checkIn));
            when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));

            checkInService.cancelCheckIn(checkInId, principal);

            verify(seatService).cancelConfirmedSeat(confirmedSeat.getId(), passengerId);
            verify(checkInRepository).save(argThat(ci -> ci.getStatus() == CheckInStatus.CANCELLED));
        }
    }
}

