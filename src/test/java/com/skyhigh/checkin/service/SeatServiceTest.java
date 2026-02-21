package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
import com.skyhigh.checkin.dto.response.SeatMapResponse;
import com.skyhigh.checkin.exception.ResourceNotFoundException;
import com.skyhigh.checkin.model.entity.Flight;
import com.skyhigh.checkin.model.entity.Seat;
import com.skyhigh.checkin.model.enums.FlightStatus;
import com.skyhigh.checkin.model.enums.SeatClass;
import com.skyhigh.checkin.model.enums.SeatStatus;
import com.skyhigh.checkin.repository.CheckInRepository;
import com.skyhigh.checkin.repository.PassengerRepository;
import com.skyhigh.checkin.repository.SeatAuditLogRepository;
import com.skyhigh.checkin.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private SeatAuditLogRepository auditLogRepository;

    @Mock
    private SeatLockService seatLockService;

    @Mock
    private CheckInConfig checkInConfig;

    @InjectMocks
    private SeatService seatService;

    private UUID flightId;
    private Flight flight;
    private Seat seat1;
    private Seat seat2;

    @BeforeEach
    void setUp() {
        flightId = UUID.randomUUID();

        flight = Flight.builder()
                .id(flightId)
                .flightNumber("SH101")
                .departureTime(LocalDateTime.now().plusHours(20))
                .arrivalTime(LocalDateTime.now().plusHours(22))
                .origin("DEL")
                .destination("BOM")
                .status(FlightStatus.SCHEDULED)
                .totalSeats(180)
                .build();

        seat1 = Seat.builder()
                .id(UUID.randomUUID())
                .flight(flight)
                .seatNumber("1A")
                .seatClass(SeatClass.FIRST)
                .status(SeatStatus.AVAILABLE)
                .build();

        seat2 = Seat.builder()
                .id(UUID.randomUUID())
                .flight(flight)
                .seatNumber("1B")
                .seatClass(SeatClass.FIRST)
                .status(SeatStatus.HELD)
                .heldUntil(LocalDateTime.now().plusSeconds(100))
                .build();
    }

    @Test
    void getSeatMap_ShouldReturnSeatMap_WhenSeatsExist() {
        // Given
        List<Seat> seats = Arrays.asList(seat1, seat2);
        when(seatRepository.findByFlightIdOrderBySeatClassAndNumber(flightId)).thenReturn(seats);

        // When
        SeatMapResponse response = seatService.getSeatMap(flightId);

        // Then
        assertNotNull(response);
        assertEquals(flightId, response.getFlightId());
        assertEquals("SH101", response.getFlightNumber());
        assertEquals(2, response.getSummary().getTotal());
        assertEquals(1, response.getSummary().getAvailable());
        assertEquals(1, response.getSummary().getHeld());

        verify(seatRepository).findByFlightIdOrderBySeatClassAndNumber(flightId);
    }

    @Test
    void getSeatMap_ShouldThrowException_WhenNoSeatsFound() {
        // Given
        when(seatRepository.findByFlightIdOrderBySeatClassAndNumber(flightId)).thenReturn(List.of());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> seatService.getSeatMap(flightId));
    }
}

