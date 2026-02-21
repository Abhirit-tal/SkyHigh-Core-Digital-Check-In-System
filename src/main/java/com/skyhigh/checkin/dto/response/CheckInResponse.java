package com.skyhigh.checkin.dto.response;

import com.skyhigh.checkin.model.enums.CheckInStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInResponse {

    private UUID checkInId;
    private CheckInStatus status;
    private FlightInfo flight;
    private PassengerInfo passenger;
    private SeatInfo seat;
    private BaggageInfo baggage;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    private List<String> nextSteps;
    private BoardingPassInfo boardingPass;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlightInfo {
        private UUID flightId;
        private String flightNumber;
        private LocalDateTime departureTime;
        private String origin;
        private String destination;
        private String gate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassengerInfo {
        private UUID id;
        private String firstName;
        private String lastName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatInfo {
        private UUID seatId;
        private String seatNumber;
        private String seatClass;
        private LocalDateTime heldUntil;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BaggageInfo {
        private java.math.BigDecimal weightKg;
        private java.math.BigDecimal maxAllowedKg;
        private java.math.BigDecimal excessKg;
        private java.math.BigDecimal excessFee;
        private String currency;
        private boolean paymentRequired;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoardingPassInfo {
        private UUID id;
        private String barcode;
        private String downloadUrl;
    }
}

