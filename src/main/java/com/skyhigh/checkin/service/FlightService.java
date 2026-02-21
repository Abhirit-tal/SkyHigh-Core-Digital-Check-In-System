package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
import com.skyhigh.checkin.dto.response.FlightResponse;
import com.skyhigh.checkin.exception.ResourceNotFoundException;
import com.skyhigh.checkin.model.entity.Flight;
import com.skyhigh.checkin.model.enums.SeatStatus;
import com.skyhigh.checkin.repository.FlightRepository;
import com.skyhigh.checkin.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlightService {

    private final FlightRepository flightRepository;
    private final SeatRepository seatRepository;
    private final CheckInConfig checkInConfig;

    @Transactional(readOnly = true)
    public FlightResponse getFlightById(UUID flightId) {
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", flightId));

        return buildFlightResponse(flight);
    }

    private FlightResponse buildFlightResponse(Flight flight) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkInOpens = flight.getDepartureTime().minusHours(checkInConfig.getCheckinWindowOpensHours());
        LocalDateTime checkInCloses = flight.getDepartureTime().minusHours(checkInConfig.getCheckinWindowClosesHours());
        boolean checkInOpen = now.isAfter(checkInOpens) && now.isBefore(checkInCloses);

        long available = seatRepository.countByFlightIdAndStatus(flight.getId(), SeatStatus.AVAILABLE);
        long held = seatRepository.countByFlightIdAndStatus(flight.getId(), SeatStatus.HELD);
        long confirmed = seatRepository.countByFlightIdAndStatus(flight.getId(), SeatStatus.CONFIRMED);

        return FlightResponse.builder()
                .id(flight.getId())
                .flightNumber(flight.getFlightNumber())
                .departureTime(flight.getDepartureTime())
                .arrivalTime(flight.getArrivalTime())
                .origin(flight.getOrigin())
                .destination(flight.getDestination())
                .aircraftType(flight.getAircraftType())
                .status(flight.getStatus())
                .totalSeats(flight.getTotalSeats())
                .gate(flight.getGate())
                .checkInOpen(checkInOpen)
                .checkInOpensAt(checkInOpens)
                .checkInClosesAt(checkInCloses)
                .seatSummary(FlightResponse.SeatSummary.builder()
                        .totalAvailable(available)
                        .totalHeld(held)
                        .totalConfirmed(confirmed)
                        .build())
                .build();
    }
}

