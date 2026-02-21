package com.skyhigh.checkin.dto.response;

import com.skyhigh.checkin.model.enums.FlightStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightResponse {

    private UUID id;
    private String flightNumber;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private String origin;
    private String destination;
    private String aircraftType;
    private FlightStatus status;
    private Integer totalSeats;
    private String gate;
    private boolean checkInOpen;
    private LocalDateTime checkInOpensAt;
    private LocalDateTime checkInClosesAt;
    private SeatSummary seatSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatSummary {
        private long totalAvailable;
        private long totalHeld;
        private long totalConfirmed;
    }
}

