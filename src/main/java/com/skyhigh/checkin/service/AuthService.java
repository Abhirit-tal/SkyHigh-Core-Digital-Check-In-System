package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
import com.skyhigh.checkin.dto.request.LoginRequest;
import com.skyhigh.checkin.dto.request.RefreshTokenRequest;
import com.skyhigh.checkin.dto.response.LoginResponse;
import com.skyhigh.checkin.exception.InvalidCredentialsException;
import com.skyhigh.checkin.exception.ResourceNotFoundException;
import com.skyhigh.checkin.model.entity.Booking;
import com.skyhigh.checkin.model.entity.Passenger;
import com.skyhigh.checkin.model.enums.BookingStatus;
import com.skyhigh.checkin.repository.BookingRepository;
import com.skyhigh.checkin.repository.PassengerRepository;
import com.skyhigh.checkin.security.JwtTokenProvider;
import com.skyhigh.checkin.security.PassengerPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final BookingRepository bookingRepository;
    private final PassengerRepository passengerRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final CheckInConfig checkInConfig;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for booking reference: {}", request.getBookingReference());

        // Find booking with passenger and flight details
        Booking booking = bookingRepository.findByBookingReferenceAndPassengerDetails(
                request.getBookingReference().toUpperCase(),
                request.getLastName(),
                request.getEmail()
        ).orElseThrow(() -> {
            log.warn("Invalid login attempt for booking: {}", request.getBookingReference());
            return new InvalidCredentialsException();
        });

        if (!booking.isActive()) {
            log.warn("Login attempt for inactive booking: {}", request.getBookingReference());
            throw new InvalidCredentialsException("Booking is no longer active");
        }

        Passenger passenger = booking.getPassenger();

        // Get all active bookings for this passenger
        List<Booking> activeBookings = bookingRepository.findByPassengerIdAndStatus(
                passenger.getId(), BookingStatus.ACTIVE);

        List<UUID> flightIds = activeBookings.stream()
                .map(b -> b.getFlight().getId())
                .collect(Collectors.toList());

        // Create principal and generate tokens
        PassengerPrincipal principal = PassengerPrincipal.builder()
                .passengerId(passenger.getId())
                .email(passenger.getEmail())
                .firstName(passenger.getFirstName())
                .lastName(passenger.getLastName())
                .flightIds(flightIds)
                .build();

        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal);

        // Build flight info list
        List<LoginResponse.FlightInfo> flightInfos = activeBookings.stream()
                .map(b -> buildFlightInfo(b.getFlight()))
                .collect(Collectors.toList());

        log.info("Successful login for passenger: {}", passenger.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .passenger(LoginResponse.PassengerInfo.builder()
                        .id(passenger.getId())
                        .firstName(passenger.getFirstName())
                        .lastName(passenger.getLastName())
                        .email(passenger.getEmail())
                        .build())
                .flights(flightInfos)
                .build();
    }

    @Transactional(readOnly = true)
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        log.info("Refresh token attempt");

        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new InvalidCredentialsException("Invalid or expired refresh token");
        }

        UUID passengerId = jwtTokenProvider.getPassengerIdFromRefreshToken(request.getRefreshToken());

        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Passenger", passengerId));

        List<Booking> activeBookings = bookingRepository.findByPassengerIdAndStatus(
                passenger.getId(), BookingStatus.ACTIVE);

        List<UUID> flightIds = activeBookings.stream()
                .map(b -> b.getFlight().getId())
                .collect(Collectors.toList());

        PassengerPrincipal principal = PassengerPrincipal.builder()
                .passengerId(passenger.getId())
                .email(passenger.getEmail())
                .firstName(passenger.getFirstName())
                .lastName(passenger.getLastName())
                .flightIds(flightIds)
                .build();

        String accessToken = jwtTokenProvider.generateAccessToken(principal);
        String refreshToken = jwtTokenProvider.generateRefreshToken(principal);

        List<LoginResponse.FlightInfo> flightInfos = activeBookings.stream()
                .map(b -> buildFlightInfo(b.getFlight()))
                .collect(Collectors.toList());

        log.info("Successful token refresh for passenger: {}", passenger.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpiration() / 1000)
                .passenger(LoginResponse.PassengerInfo.builder()
                        .id(passenger.getId())
                        .firstName(passenger.getFirstName())
                        .lastName(passenger.getLastName())
                        .email(passenger.getEmail())
                        .build())
                .flights(flightInfos)
                .build();
    }

    private LoginResponse.FlightInfo buildFlightInfo(com.skyhigh.checkin.model.entity.Flight flight) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime checkInOpens = flight.getDepartureTime().minusHours(checkInConfig.getCheckinWindowOpensHours());
        LocalDateTime checkInCloses = flight.getDepartureTime().minusHours(checkInConfig.getCheckinWindowClosesHours());

        boolean checkInOpen = now.isAfter(checkInOpens) && now.isBefore(checkInCloses);

        return LoginResponse.FlightInfo.builder()
                .flightId(flight.getId())
                .flightNumber(flight.getFlightNumber())
                .departureTime(flight.getDepartureTime())
                .origin(flight.getOrigin())
                .destination(flight.getDestination())
                .checkInOpen(checkInOpen)
                .checkInOpensAt(checkInOpens)
                .checkInClosesAt(checkInCloses)
                .build();
    }
}

