package com.skyhigh.checkin.controller;

import com.skyhigh.checkin.dto.request.HoldSeatRequest;
import com.skyhigh.checkin.dto.response.SeatHoldResponse;
import com.skyhigh.checkin.security.PassengerPrincipal;
import com.skyhigh.checkin.service.SeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Seats", description = "Seat management APIs - hold, release, and confirm seats")
@SecurityRequirement(name = "bearerAuth")
public class SeatController {

    private final SeatService seatService;

    @PostMapping("/{seatId}/hold")
    @Operation(summary = "Hold a seat",
               description = "Reserve a seat for 120 seconds. The seat will be automatically released if not confirmed.")
    public ResponseEntity<SeatHoldResponse> holdSeat(
            @PathVariable UUID seatId,
            @Valid @RequestBody HoldSeatRequest request,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Holding seat {} for passenger {} (check-in: {})",
                seatId, principal.getPassengerId(), request.getCheckInId());
        SeatHoldResponse response = seatService.holdSeat(seatId, principal.getPassengerId(), request.getCheckInId());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{seatId}/hold")
    @Operation(summary = "Release seat hold",
               description = "Release a previously held seat")
    public ResponseEntity<Void> releaseSeatHold(
            @PathVariable UUID seatId,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Releasing seat hold {} for passenger {}", seatId, principal.getPassengerId());
        seatService.releaseSeatHold(seatId, principal.getPassengerId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{seatId}/confirm")
    @Operation(summary = "Confirm seat assignment",
               description = "Permanently confirm a held seat. This action cannot be undone.")
    public ResponseEntity<SeatHoldResponse> confirmSeat(
            @PathVariable UUID seatId,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Confirming seat {} for passenger {}", seatId, principal.getPassengerId());
        var seat = seatService.confirmSeat(seatId, principal.getPassengerId());

        SeatHoldResponse response = SeatHoldResponse.builder()
                .seatId(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .seatClass(seat.getSeatClass())
                .status(seat.getStatus())
                .build();

        return ResponseEntity.ok(response);
    }
}

