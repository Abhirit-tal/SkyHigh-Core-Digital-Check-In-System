package com.skyhigh.checkin.controller;

import com.skyhigh.checkin.dto.request.BaggageRequest;
import com.skyhigh.checkin.dto.request.PaymentRequest;
import com.skyhigh.checkin.dto.request.StartCheckInRequest;
import com.skyhigh.checkin.dto.response.CheckInResponse;
import com.skyhigh.checkin.security.PassengerPrincipal;
import com.skyhigh.checkin.service.CheckInService;
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
@RequestMapping("/api/v1/check-in")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Check-In", description = "Check-in process management APIs")
@SecurityRequirement(name = "bearerAuth")
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping("/start")
    @PreAuthorize("@flightAccessChecker.hasFlightAccess(#request.flightId)")
    @Operation(summary = "Start check-in",
               description = "Start a new check-in session for a flight. Session expires after 10 minutes of inactivity.")
    public ResponseEntity<CheckInResponse> startCheckIn(
            @Valid @RequestBody StartCheckInRequest request,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Starting check-in for flight {} by passenger {}", request.getFlightId(), principal.getPassengerId());
        CheckInResponse response = checkInService.startCheckIn(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{checkInId}")
    @PreAuthorize("@flightAccessChecker.isCheckInOwner(#checkInId)")
    @Operation(summary = "Get check-in status",
               description = "Get the current status and details of a check-in session")
    public ResponseEntity<CheckInResponse> getCheckInStatus(
            @PathVariable UUID checkInId,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Getting check-in status: {} by passenger {}", checkInId, principal.getPassengerId());
        CheckInResponse response = checkInService.getCheckInStatus(checkInId, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{checkInId}/baggage")
    @PreAuthorize("@flightAccessChecker.isCheckInOwner(#checkInId)")
    @Operation(summary = "Add baggage",
               description = "Add baggage weight to the check-in. If weight exceeds 25kg, payment will be required.")
    public ResponseEntity<CheckInResponse> addBaggage(
            @PathVariable UUID checkInId,
            @Valid @RequestBody BaggageRequest request,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Adding baggage to check-in {}: {} kg", checkInId, request.getWeightKg());
        CheckInResponse response = checkInService.addBaggage(checkInId, request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{checkInId}/payment")
    @PreAuthorize("@flightAccessChecker.isCheckInOwner(#checkInId)")
    @Operation(summary = "Process payment",
               description = "Process payment for excess baggage fee")
    public ResponseEntity<CheckInResponse> processPayment(
            @PathVariable UUID checkInId,
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Processing payment for check-in {}: {} {}", checkInId, request.getAmount(), request.getCurrency());
        CheckInResponse response = checkInService.processPayment(checkInId, request, principal);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{checkInId}/confirm")
    @PreAuthorize("@flightAccessChecker.isCheckInOwner(#checkInId)")
    @Operation(summary = "Confirm check-in",
               description = "Complete the check-in process and generate boarding pass")
    public ResponseEntity<CheckInResponse> confirmCheckIn(
            @PathVariable UUID checkInId,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Confirming check-in: {} by passenger {}", checkInId, principal.getPassengerId());
        CheckInResponse response = checkInService.confirmCheckIn(checkInId, principal);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{checkInId}")
    @PreAuthorize("@flightAccessChecker.isCheckInOwner(#checkInId)")
    @Operation(summary = "Cancel check-in",
               description = "Cancel the check-in session and release any held seats")
    public ResponseEntity<Void> cancelCheckIn(
            @PathVariable UUID checkInId,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Cancelling check-in: {} by passenger {}", checkInId, principal.getPassengerId());
        checkInService.cancelCheckIn(checkInId, principal);
        return ResponseEntity.noContent().build();
    }
}

