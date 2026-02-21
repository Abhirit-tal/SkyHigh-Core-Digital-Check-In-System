package com.skyhigh.checkin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoardingPassResponse {

    private UUID id;
    private String passengerName;
    private String flightNumber;
    private String seatNumber;
    private String seatClass;
    private String origin;
    private String destination;
    private LocalDateTime departureTime;
    private String gate;
    private LocalDateTime boardingTime;
    private String barcode;
    private String qrCodeBase64;
    private String downloadUrl;
    private LocalDateTime generatedAt;
}

