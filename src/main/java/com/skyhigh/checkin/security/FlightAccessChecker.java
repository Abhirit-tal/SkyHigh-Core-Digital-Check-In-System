package com.skyhigh.checkin.security;

import com.skyhigh.checkin.exception.FlightAccessDeniedException;
import com.skyhigh.checkin.repository.BookingRepository;
import com.skyhigh.checkin.repository.CheckInRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("flightAccessChecker")
@RequiredArgsConstructor
@Slf4j
public class FlightAccessChecker {

    private final BookingRepository bookingRepository;
    private final CheckInRepository checkInRepository;

    public boolean hasFlightAccess(UUID flightId) {
        PassengerPrincipal principal = getAuthenticatedPassenger();

        if (principal == null) {
            log.warn("No authenticated passenger found");
            return false;
        }

        // Check if flightId is in the token's authorized flights
        if (!principal.hasAccessToFlight(flightId)) {
            log.warn("Passenger {} does not have access to flight {} (not in token)",
                    principal.getPassengerId(), flightId);
            throw new FlightAccessDeniedException(principal.getPassengerId(), flightId);
        }

        // Additional DB validation for extra security
        boolean hasActiveBooking = bookingRepository.existsByPassengerIdAndFlightIdAndStatus(
                principal.getPassengerId(),
                flightId,
                com.skyhigh.checkin.model.enums.BookingStatus.ACTIVE
        );

        if (!hasActiveBooking) {
            log.warn("Passenger {} does not have active booking for flight {}",
                    principal.getPassengerId(), flightId);
            throw new FlightAccessDeniedException(principal.getPassengerId(), flightId);
        }

        return true;
    }

    public boolean isCheckInOwner(UUID checkInId) {
        PassengerPrincipal principal = getAuthenticatedPassenger();

        if (principal == null) {
            return false;
        }

        return checkInRepository.findByIdWithDetails(checkInId)
                .map(checkIn -> checkIn.getBooking().getPassenger().getId().equals(principal.getPassengerId()))
                .orElse(false);
    }

    public PassengerPrincipal getAuthenticatedPassenger() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PassengerPrincipal) {
            return (PassengerPrincipal) authentication.getPrincipal();
        }
        return null;
    }
}

