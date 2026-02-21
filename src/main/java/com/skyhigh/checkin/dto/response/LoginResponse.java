package com.skyhigh.checkin.dto.response;

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
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private PassengerInfo passenger;
    private List<FlightInfo> flights;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassengerInfo {
        private UUID id;
        private String firstName;
        private String lastName;
        private String email;
    }

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
        private boolean checkInOpen;
        private LocalDateTime checkInOpensAt;
        private LocalDateTime checkInClosesAt;
    }
}

