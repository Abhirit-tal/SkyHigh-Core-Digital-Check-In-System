package com.skyhigh.checkin.controller;

import com.skyhigh.checkin.dto.request.JoinWaitlistRequest;
import com.skyhigh.checkin.dto.response.WaitlistResponse;
import com.skyhigh.checkin.model.entity.WaitlistEntry;
import com.skyhigh.checkin.security.PassengerPrincipal;
import com.skyhigh.checkin.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Waitlist", description = "Seat waitlist management APIs")
@SecurityRequirement(name = "bearerAuth")
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping("/flights/{flightId}/waitlist")
    @PreAuthorize("@flightAccessChecker.hasFlightAccess(#flightId)")
    @Operation(summary = "Join waitlist",
               description = "Join the waitlist for a flight when no seats are available. " +
                             "You will be notified when a seat becomes available.")
    public ResponseEntity<WaitlistResponse> joinWaitlist(
            @PathVariable UUID flightId,
            @Valid @RequestBody JoinWaitlistRequest request,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Passenger {} joining waitlist for flight {}", principal.getPassengerId(), flightId);

        WaitlistEntry entry = waitlistService.joinWaitlist(
                flightId, principal.getPassengerId(), request.getPreferredSeatClass());

        WaitlistResponse response = buildWaitlistResponse(entry, flightId, principal.getPassengerId());
        response.setMessage("Successfully added to the waitlist. You will be notified when a seat becomes available.");

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/flights/{flightId}/waitlist")
    @PreAuthorize("@flightAccessChecker.hasFlightAccess(#flightId)")
    @Operation(summary = "Leave waitlist",
               description = "Remove yourself from the waitlist for a flight")
    public ResponseEntity<Void> leaveWaitlist(
            @PathVariable UUID flightId,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Passenger {} leaving waitlist for flight {}", principal.getPassengerId(), flightId);
        waitlistService.leaveWaitlist(flightId, principal.getPassengerId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/flights/{flightId}/waitlist/status")
    @PreAuthorize("@flightAccessChecker.hasFlightAccess(#flightId)")
    @Operation(summary = "Get waitlist status",
               description = "Get your current position and status on the waitlist")
    public ResponseEntity<WaitlistResponse> getWaitlistStatus(
            @PathVariable UUID flightId,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Getting waitlist status for passenger {} on flight {}", principal.getPassengerId(), flightId);

        WaitlistService.WaitlistStatusInfo status = waitlistService.getWaitlistStatus(flightId, principal.getPassengerId());

        WaitlistResponse response = WaitlistResponse.builder()
                .entryId(status.entryId())
                .flightId(flightId)
                .passengerId(principal.getPassengerId())
                .status(status.status())
                .preferredSeatClass(status.preferredSeatClass())
                .position(status.position())
                .totalWaiting(status.totalWaiting())
                .joinedAt(status.joinedAt())
                .offeredAt(status.offeredAt())
                .offerExpiresAt(status.offerExpiresAt())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/waitlist/{entryId}/accept")
    @Operation(summary = "Accept waitlist offer",
               description = "Accept a seat offer from the waitlist. The seat will be held for you to complete check-in.")
    public ResponseEntity<WaitlistResponse> acceptOffer(
            @PathVariable UUID entryId,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Passenger {} accepting waitlist offer {}", principal.getPassengerId(), entryId);

        WaitlistEntry entry = waitlistService.acceptOffer(entryId, principal.getPassengerId());

        WaitlistResponse response = WaitlistResponse.builder()
                .entryId(entry.getId())
                .flightId(entry.getFlight().getId())
                .passengerId(principal.getPassengerId())
                .status(entry.getStatus())
                .message("Seat offer accepted! Please proceed to check-in to confirm your seat.")
                .build();

        if (entry.getAssignedSeat() != null) {
            response.setOfferedSeat(WaitlistResponse.SeatInfo.builder()
                    .seatId(entry.getAssignedSeat().getId())
                    .seatNumber(entry.getAssignedSeat().getSeatNumber())
                    .seatClass(entry.getAssignedSeat().getSeatClass().name())
                    .build());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/waitlist/{entryId}/decline")
    @Operation(summary = "Decline waitlist offer",
               description = "Decline a seat offer. The seat will be offered to the next passenger in the queue.")
    public ResponseEntity<Void> declineOffer(
            @PathVariable UUID entryId,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Passenger {} declining waitlist offer {}", principal.getPassengerId(), entryId);
        waitlistService.declineOffer(entryId, principal.getPassengerId());
        return ResponseEntity.noContent().build();
    }

    private WaitlistResponse buildWaitlistResponse(WaitlistEntry entry, UUID flightId, UUID passengerId) {
        return WaitlistResponse.builder()
                .entryId(entry.getId())
                .flightId(flightId)
                .passengerId(passengerId)
                .status(entry.getStatus())
                .preferredSeatClass(entry.getPreferredSeatClass())
                .position(entry.getPriority())
                .joinedAt(entry.getJoinedAt())
                .build();
    }
}

