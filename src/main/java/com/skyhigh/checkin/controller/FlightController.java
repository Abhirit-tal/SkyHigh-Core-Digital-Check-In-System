package com.skyhigh.checkin.controller;

import com.skyhigh.checkin.dto.response.FlightResponse;
import com.skyhigh.checkin.dto.response.SeatMapResponse;
import com.skyhigh.checkin.security.PassengerPrincipal;
import com.skyhigh.checkin.service.FlightService;
import com.skyhigh.checkin.service.SeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flights")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Flights", description = "Flight information and seat map APIs")
@SecurityRequirement(name = "bearerAuth")
public class FlightController {

    private final FlightService flightService;
    private final SeatService seatService;

    @GetMapping("/{flightId}")
    @PreAuthorize("@flightAccessChecker.hasFlightAccess(#flightId)")
    @Operation(summary = "Get flight details",
               description = "Get detailed information about a specific flight")
    public ResponseEntity<FlightResponse> getFlightById(
            @PathVariable UUID flightId,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Getting flight details: {} by passenger: {}", flightId, principal.getPassengerId());
        FlightResponse response = flightService.getFlightById(flightId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{flightId}/seats")
    @PreAuthorize("@flightAccessChecker.hasFlightAccess(#flightId)")
    @Operation(summary = "Get seat map",
               description = "Get the seat map for a specific flight showing availability")
    public ResponseEntity<SeatMapResponse> getSeatMap(
            @PathVariable UUID flightId,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Getting seat map for flight: {} by passenger: {}", flightId, principal.getPassengerId());
        SeatMapResponse response = seatService.getSeatMap(flightId);
        return ResponseEntity.ok(response);
    }
}

