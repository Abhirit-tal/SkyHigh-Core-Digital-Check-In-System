package com.skyhigh.checkin.exception;

import com.skyhigh.checkin.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ============ AUTHENTICATION EXCEPTIONS ============

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex, request, null);
    }

    // ============ AUTHORIZATION EXCEPTIONS ============

    @ExceptionHandler(FlightAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleFlightAccessDenied(
            FlightAccessDeniedException ex, HttpServletRequest request) {
        log.warn("Flight access denied: passengerId={}, flightId={}",
                ex.getPassengerId(), ex.getFlightId());
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex, request, null);
    }

    // ============ RESOURCE NOT FOUND EXCEPTIONS ============

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex, request, null);
    }

    // ============ CONFLICT EXCEPTIONS ============

    @ExceptionHandler(SeatAlreadyHeldException.class)
    public ResponseEntity<ErrorResponse> handleSeatAlreadyHeld(
            SeatAlreadyHeldException ex, HttpServletRequest request) {
        log.warn("Seat already held: {} until {}", ex.getSeatNumber(), ex.getHeldUntil());

        List<ErrorResponse.Suggestion> suggestions = List.of(
                ErrorResponse.Suggestion.builder()
                        .action("VIEW_AVAILABLE_SEATS")
                        .endpoint("GET /api/v1/flights/" + ex.getFlightId() + "/seats?status=AVAILABLE")
                        .message("View other available seats on this flight")
                        .build()
        );

        return buildErrorResponse(HttpStatus.CONFLICT, ex, request, suggestions);
    }

    @ExceptionHandler(SeatAlreadyConfirmedException.class)
    public ResponseEntity<ErrorResponse> handleSeatAlreadyConfirmed(
            SeatAlreadyConfirmedException ex, HttpServletRequest request) {
        log.warn("Seat already confirmed: {}", ex.getSeatNumber());

        List<ErrorResponse.Suggestion> suggestions = List.of(
                ErrorResponse.Suggestion.builder()
                        .action("VIEW_AVAILABLE_SEATS")
                        .endpoint("GET /api/v1/flights/" + ex.getFlightId() + "/seats?status=AVAILABLE")
                        .message("View other available seats on this flight")
                        .build()
        );

        return buildErrorResponse(HttpStatus.CONFLICT, ex, request, suggestions);
    }

    @ExceptionHandler(CheckInAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCheckInAlreadyExists(
            CheckInAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Check-in already exists: {}", ex.getExistingCheckInId());

        List<ErrorResponse.Suggestion> suggestions = List.of(
                ErrorResponse.Suggestion.builder()
                        .action("GET_EXISTING_CHECK_IN")
                        .endpoint("GET /api/v1/check-in/" + ex.getExistingCheckInId())
                        .message("View your existing check-in session")
                        .build()
        );

        return buildErrorResponse(HttpStatus.CONFLICT, ex, request, suggestions);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Optimistic lock failure: {}", ex.getMessage());

        SkyHighBaseException customEx = new SkyHighBaseException(
                "The resource was modified by another request. Please retry.",
                "CONCURRENT_UPDATE", true, 1) {};

        return buildErrorResponse(HttpStatus.CONFLICT, customEx, request, null);
    }

    // ============ BUSINESS RULE EXCEPTIONS ============

    @ExceptionHandler(CheckInWindowNotOpenException.class)
    public ResponseEntity<ErrorResponse> handleCheckInNotOpen(
            CheckInWindowNotOpenException ex, HttpServletRequest request) {
        log.info("Check-in window not open: opens at {}", ex.getOpensAt());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex, request, null);
    }

    @ExceptionHandler(CheckInWindowClosedException.class)
    public ResponseEntity<ErrorResponse> handleCheckInClosed(
            CheckInWindowClosedException ex, HttpServletRequest request) {
        log.info("Check-in window closed: {}", ex.getClosedAt());

        List<ErrorResponse.Suggestion> suggestions = List.of(
                ErrorResponse.Suggestion.builder()
                        .action("AIRPORT_COUNTER")
                        .endpoint(null)
                        .message("Please check in at the airport counter")
                        .build()
        );

        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex, request, suggestions);
    }

    @ExceptionHandler(SeatHoldExpiredException.class)
    public ResponseEntity<ErrorResponse> handleSeatHoldExpired(
            SeatHoldExpiredException ex, HttpServletRequest request) {
        log.warn("Seat hold expired: {} at {}", ex.getSeatNumber(), ex.getExpiredAt());

        List<ErrorResponse.Suggestion> suggestions = List.of(
                ErrorResponse.Suggestion.builder()
                        .action("RESELECT_SEAT")
                        .endpoint("POST /api/v1/seats/{seatId}/hold")
                        .message("Select the seat again if still available")
                        .build()
        );

        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex, request, suggestions);
    }

    @ExceptionHandler(BaggageWeightExceededException.class)
    public ResponseEntity<ErrorResponse> handleBaggageExceeded(
            BaggageWeightExceededException ex, HttpServletRequest request) {
        log.info("Baggage weight exceeded: {} kg (max: {} kg, fee: {})",
                ex.getActualWeight(), ex.getMaxWeight(), ex.getFee());

        List<ErrorResponse.Suggestion> suggestions = List.of(
                ErrorResponse.Suggestion.builder()
                        .action("PAY_EXCESS_FEE")
                        .endpoint("POST /api/v1/check-in/{checkInId}/payment")
                        .message("Pay the excess baggage fee to continue")
                        .build()
        );

        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex, request, suggestions);
    }

    @ExceptionHandler(PaymentRequiredException.class)
    public ResponseEntity<ErrorResponse> handlePaymentRequired(
            PaymentRequiredException ex, HttpServletRequest request) {
        log.info("Payment required: {} {}", ex.getAmount(), ex.getCurrency());
        return buildErrorResponse(HttpStatus.PAYMENT_REQUIRED, ex, request, null);
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ErrorResponse> handlePaymentFailed(
            PaymentFailedException ex, HttpServletRequest request) {
        log.warn("Payment failed: {}", ex.getMessage());

        List<ErrorResponse.Suggestion> suggestions = List.of(
                ErrorResponse.Suggestion.builder()
                        .action("RETRY_PAYMENT")
                        .endpoint("POST /api/v1/check-in/{checkInId}/payment")
                        .message("Try with a different payment method")
                        .build()
        );

        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex, request, suggestions);
    }

    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<ErrorResponse> handleSessionExpired(
            SessionExpiredException ex, HttpServletRequest request) {
        log.info("Check-in session expired");

        List<ErrorResponse.Suggestion> suggestions = List.of(
                ErrorResponse.Suggestion.builder()
                        .action("START_NEW_CHECK_IN")
                        .endpoint("POST /api/v1/check-in/start")
                        .message("Start a new check-in session")
                        .build()
        );

        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex, request, suggestions);
    }

    @ExceptionHandler(InvalidSeatStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSeatState(
            InvalidSeatStateException ex, HttpServletRequest request) {
        log.warn("Invalid seat state: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex, request, null);
    }

    @ExceptionHandler(BookingNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleBookingNotActive(
            BookingNotActiveException ex, HttpServletRequest request) {
        log.warn("Booking not active: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex, request, null);
    }

    // ============ VALIDATION EXCEPTIONS ============

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());

        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());

        String requestId = UUID.randomUUID().toString();

        ErrorResponse response = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("VALIDATION_FAILED")
                        .message("Request validation failed")
                        .retryable(false)
                        .fieldErrors(fieldErrors)
                        .build())
                .meta(ErrorResponse.Meta.builder()
                        .timestamp(LocalDateTime.now())
                        .requestId(requestId)
                        .path(request.getRequestURI())
                        .build())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // ============ CATCH-ALL HANDLER ============

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);

        String requestId = UUID.randomUUID().toString();

        ErrorResponse response = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code("INTERNAL_ERROR")
                        .message("An unexpected error occurred. Please try again or contact support.")
                        .details(ex.getMessage())
                        .retryable(true)
                        .retryAfterSeconds(5)
                        .build())
                .meta(ErrorResponse.Meta.builder()
                        .timestamp(LocalDateTime.now())
                        .requestId(requestId)
                        .path(request.getRequestURI())
                        .build())
                .suggestions(List.of(
                        ErrorResponse.Suggestion.builder()
                                .action("CONTACT_SUPPORT")
                                .message("Contact support with request ID: " + requestId)
                                .build()
                ))
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // ============ HELPER METHODS ============

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status,
            SkyHighBaseException ex,
            HttpServletRequest request,
            List<ErrorResponse.Suggestion> suggestions) {

        String requestId = UUID.randomUUID().toString();

        ErrorResponse response = ErrorResponse.builder()
                .error(ErrorResponse.ErrorDetail.builder()
                        .code(ex.getErrorCode())
                        .message(ex.getMessage())
                        .retryable(ex.isRetryable())
                        .retryAfterSeconds(ex.getRetryAfterSeconds())
                        .build())
                .meta(ErrorResponse.Meta.builder()
                        .timestamp(LocalDateTime.now())
                        .requestId(requestId)
                        .path(request.getRequestURI())
                        .build())
                .suggestions(suggestions)
                .build();

        return ResponseEntity.status(status).body(response);
    }
}

