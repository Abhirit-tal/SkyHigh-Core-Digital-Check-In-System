package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
import com.skyhigh.checkin.dto.request.BaggageRequest;
import com.skyhigh.checkin.dto.request.PaymentRequest;
import com.skyhigh.checkin.dto.request.StartCheckInRequest;
import com.skyhigh.checkin.dto.response.CheckInResponse;
import com.skyhigh.checkin.exception.*;
import com.skyhigh.checkin.model.entity.*;
import com.skyhigh.checkin.model.enums.CheckInStatus;
import com.skyhigh.checkin.model.enums.PaymentStatus;
import com.skyhigh.checkin.repository.BookingRepository;
import com.skyhigh.checkin.repository.CheckInRepository;
import com.skyhigh.checkin.security.PassengerPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final BookingRepository bookingRepository;
    private final SeatService seatService;
    private final WeightService weightService;
    private final PaymentService paymentService;
    private final BoardingPassService boardingPassService;
    private final CheckInConfig checkInConfig;

    @Transactional
    public CheckInResponse startCheckIn(StartCheckInRequest request, PassengerPrincipal principal) {
        log.info("Starting check-in for flight {} by passenger {}", request.getFlightId(), principal.getPassengerId());

        // Find the booking
        Booking booking = bookingRepository.findActiveBookingByPassengerAndFlight(
                principal.getPassengerId(), request.getFlightId()
        ).orElseThrow(() -> new ResourceNotFoundException("Booking",
                "passenger " + principal.getPassengerId() + " on flight " + request.getFlightId()));

        if (!booking.isActive()) {
            throw new BookingNotActiveException(booking.getBookingReference());
        }

        // Validate check-in window
        validateCheckInWindow(booking.getFlight());

        // Check for existing active check-in
        checkInRepository.findActiveCheckInByBookingId(booking.getId())
                .ifPresent(existing -> {
                    throw new CheckInAlreadyExistsException(existing.getId());
                });

        // Create new check-in session
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(checkInConfig.getSessionTimeoutMinutes());

        CheckIn checkIn = CheckIn.builder()
                .booking(booking)
                .status(CheckInStatus.IN_PROGRESS)
                .startedAt(now)
                .lastActivityAt(now)
                .expiresAt(expiresAt)
                .build();

        checkIn = checkInRepository.save(checkIn);
        log.info("Check-in session created: {} for booking {}", checkIn.getId(), booking.getBookingReference());

        return buildCheckInResponse(checkIn);
    }

    @Transactional(readOnly = true)
    public CheckInResponse getCheckInStatus(UUID checkInId, PassengerPrincipal principal) {
        CheckIn checkIn = getAndValidateCheckIn(checkInId, principal);
        return buildCheckInResponse(checkIn);
    }

    @Transactional
    public CheckInResponse addBaggage(UUID checkInId, BaggageRequest request, PassengerPrincipal principal) {
        log.info("Adding baggage to check-in {}: {} kg", checkInId, request.getWeightKg());

        CheckIn checkIn = getAndValidateCheckIn(checkInId, principal);
        validateSessionNotExpired(checkIn);

        if (checkIn.isCompleted()) {
            throw new InvalidSeatStateException("Check-in is already completed");
        }

        // Validate weight using weight service
        WeightService.WeightValidationResult result = weightService.validateWeight(request.getWeightKg());

        checkIn.setBaggageWeight(result.weightKg());
        checkIn.updateActivity();
        extendSessionIfNeeded(checkIn);

        if (!result.withinLimit()) {
            // Excess baggage - require payment
            checkIn.setExcessBaggageFee(result.excessFee());
            checkIn.setStatus(CheckInStatus.WAITING_PAYMENT);
            checkIn.setPaymentStatus(PaymentStatus.PENDING);
            log.info("Excess baggage detected: {} kg over limit, fee: {}", result.excessKg(), result.excessFee());
        }

        checkIn = checkInRepository.save(checkIn);
        return buildCheckInResponse(checkIn);
    }

    @Transactional
    public CheckInResponse processPayment(UUID checkInId, PaymentRequest request, PassengerPrincipal principal) {
        log.info("Processing payment for check-in {}: {} {}", checkInId, request.getAmount(), request.getCurrency());

        CheckIn checkIn = getAndValidateCheckIn(checkInId, principal);
        validateSessionNotExpired(checkIn);

        if (!checkIn.isWaitingPayment()) {
            throw new InvalidSeatStateException("Check-in is not waiting for payment");
        }

        // Validate payment amount
        if (checkIn.getExcessBaggageFee() != null &&
            request.getAmount().compareTo(checkIn.getExcessBaggageFee()) < 0) {
            throw new PaymentRequiredException(checkIn.getExcessBaggageFee());
        }

        // Process payment
        PaymentService.PaymentResult paymentResult = paymentService.processPayment(
                request.getAmount(),
                request.getCurrency(),
                request.getIdempotencyKey()
        );

        checkIn.updateActivity();
        extendSessionIfNeeded(checkIn);

        if ("COMPLETED".equals(paymentResult.status())) {
            checkIn.setPaymentStatus(PaymentStatus.COMPLETED);
            checkIn.setPaymentReference(paymentResult.reference());
            checkIn.setStatus(CheckInStatus.IN_PROGRESS);
            log.info("Payment successful for check-in {}: {}", checkInId, paymentResult.reference());
        } else if ("DECLINED".equals(paymentResult.status())) {
            checkIn.setPaymentStatus(PaymentStatus.DECLINED);
            throw new PaymentFailedException(paymentResult.declineReason());
        } else {
            checkIn.setPaymentStatus(PaymentStatus.FAILED);
            throw new PaymentFailedException("Payment processing failed");
        }

        checkIn = checkInRepository.save(checkIn);
        return buildCheckInResponse(checkIn);
    }

    @Transactional
    public CheckInResponse confirmCheckIn(UUID checkInId, PassengerPrincipal principal) {
        log.info("Confirming check-in {}", checkInId);

        CheckIn checkIn = getAndValidateCheckIn(checkInId, principal);
        validateSessionNotExpired(checkIn);

        if (checkIn.isCompleted()) {
            throw new InvalidSeatStateException("Check-in is already completed");
        }

        if (checkIn.isWaitingPayment()) {
            throw new PaymentRequiredException(checkIn.getExcessBaggageFee());
        }

        // Validate seat is selected
        if (checkIn.getSeat() == null) {
            throw new InvalidSeatStateException("Please select a seat before confirming check-in");
        }

        // Confirm the seat
        Seat confirmedSeat = seatService.confirmSeat(checkIn.getSeat().getId(), principal.getPassengerId());

        // Complete the check-in
        checkIn.setSeat(confirmedSeat);
        checkIn.setStatus(CheckInStatus.COMPLETED);
        checkIn.setCompletedAt(LocalDateTime.now());
        checkIn = checkInRepository.save(checkIn);

        // Generate boarding pass
        BoardingPass boardingPass = boardingPassService.generateBoardingPass(checkIn);

        log.info("Check-in {} completed successfully", checkInId);
        return buildCheckInResponse(checkIn, boardingPass);
    }

    @Transactional
    public void cancelCheckIn(UUID checkInId, PassengerPrincipal principal) {
        log.info("Cancelling check-in {}", checkInId);

        CheckIn checkIn = getAndValidateCheckIn(checkInId, principal);

        if (checkIn.isCompleted()) {
            throw new InvalidSeatStateException("Cannot cancel a completed check-in");
        }

        // Release held seat if any
        if (checkIn.getSeat() != null && checkIn.getSeat().isHeld()) {
            seatService.releaseSeatHold(checkIn.getSeat().getId(), principal.getPassengerId());
        }

        checkIn.setStatus(CheckInStatus.CANCELLED);
        checkInRepository.save(checkIn);

        log.info("Check-in {} cancelled", checkInId);
    }

    private CheckIn getAndValidateCheckIn(UUID checkInId, PassengerPrincipal principal) {
        CheckIn checkIn = checkInRepository.findByIdWithDetails(checkInId)
                .orElseThrow(() -> new ResourceNotFoundException("Check-in", checkInId));

        // Verify ownership
        if (!checkIn.getBooking().getPassenger().getId().equals(principal.getPassengerId())) {
            throw new FlightAccessDeniedException(principal.getPassengerId(),
                    checkIn.getBooking().getFlight().getId());
        }

        return checkIn;
    }

    private void validateCheckInWindow(Flight flight) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkInOpens = flight.getDepartureTime().minusHours(checkInConfig.getCheckinWindowOpensHours());
        LocalDateTime checkInCloses = flight.getDepartureTime().minusHours(checkInConfig.getCheckinWindowClosesHours());

        if (now.isBefore(checkInOpens)) {
            throw new CheckInWindowNotOpenException(checkInOpens);
        }

        if (now.isAfter(checkInCloses)) {
            throw new CheckInWindowClosedException(checkInCloses);
        }
    }

    private void validateSessionNotExpired(CheckIn checkIn) {
        if (checkIn.isExpired()) {
            checkIn.setStatus(CheckInStatus.EXPIRED);
            checkInRepository.save(checkIn);
            throw new SessionExpiredException();
        }
    }

    private void extendSessionIfNeeded(CheckIn checkIn) {
        LocalDateTime newExpiry = LocalDateTime.now().plusMinutes(checkInConfig.getSessionTimeoutMinutes());
        checkIn.setExpiresAt(newExpiry);
    }

    private CheckInResponse buildCheckInResponse(CheckIn checkIn) {
        return buildCheckInResponse(checkIn, null);
    }

    private CheckInResponse buildCheckInResponse(CheckIn checkIn, BoardingPass boardingPass) {
        Booking booking = checkIn.getBooking();
        Flight flight = booking.getFlight();
        Passenger passenger = booking.getPassenger();

        CheckInResponse.CheckInResponseBuilder builder = CheckInResponse.builder()
                .checkInId(checkIn.getId())
                .status(checkIn.getStatus())
                .startedAt(checkIn.getStartedAt())
                .expiresAt(checkIn.getExpiresAt())
                .completedAt(checkIn.getCompletedAt())
                .flight(CheckInResponse.FlightInfo.builder()
                        .flightId(flight.getId())
                        .flightNumber(flight.getFlightNumber())
                        .departureTime(flight.getDepartureTime())
                        .origin(flight.getOrigin())
                        .destination(flight.getDestination())
                        .gate(flight.getGate())
                        .build())
                .passenger(CheckInResponse.PassengerInfo.builder()
                        .id(passenger.getId())
                        .firstName(passenger.getFirstName())
                        .lastName(passenger.getLastName())
                        .build());

        // Add seat info if selected
        if (checkIn.getSeat() != null) {
            Seat seat = checkIn.getSeat();
            builder.seat(CheckInResponse.SeatInfo.builder()
                    .seatId(seat.getId())
                    .seatNumber(seat.getSeatNumber())
                    .seatClass(seat.getSeatClass().name())
                    .heldUntil(seat.getHeldUntil())
                    .build());
        }

        // Add baggage info if provided
        if (checkIn.getBaggageWeight() != null) {
            BigDecimal excessKg = BigDecimal.ZERO;
            if (checkIn.getBaggageWeight().compareTo(checkInConfig.getMaxBaggageWeightKg()) > 0) {
                excessKg = checkIn.getBaggageWeight().subtract(checkInConfig.getMaxBaggageWeightKg());
            }
            builder.baggage(CheckInResponse.BaggageInfo.builder()
                    .weightKg(checkIn.getBaggageWeight())
                    .maxAllowedKg(checkInConfig.getMaxBaggageWeightKg())
                    .excessKg(excessKg)
                    .excessFee(checkIn.getExcessBaggageFee())
                    .currency("INR")
                    .paymentRequired(checkIn.isWaitingPayment())
                    .build());
        }

        // Add next steps
        builder.nextSteps(determineNextSteps(checkIn));

        // Add boarding pass if completed
        if (boardingPass != null) {
            builder.boardingPass(CheckInResponse.BoardingPassInfo.builder()
                    .id(boardingPass.getId())
                    .barcode(boardingPass.getBarcodeData())
                    .downloadUrl("/api/v1/boarding-pass/" + checkIn.getId() + "/download")
                    .build());
        }

        return builder.build();
    }

    private List<String> determineNextSteps(CheckIn checkIn) {
        List<String> steps = new ArrayList<>();

        if (checkIn.isCompleted()) {
            steps.add("DOWNLOAD_BOARDING_PASS");
            return steps;
        }

        if (checkIn.isWaitingPayment()) {
            steps.add("PROCESS_PAYMENT");
            return steps;
        }

        if (checkIn.getSeat() == null) {
            steps.add("SELECT_SEAT");
        }

        if (checkIn.getBaggageWeight() == null) {
            steps.add("ADD_BAGGAGE");
        }

        if (checkIn.getSeat() != null &&
            (checkIn.getBaggageWeight() == null || !checkIn.isWaitingPayment())) {
            steps.add("CONFIRM_CHECK_IN");
        }

        return steps;
    }
}

