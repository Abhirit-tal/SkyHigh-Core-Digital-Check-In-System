package com.skyhigh.checkin.repository;

import com.skyhigh.checkin.model.entity.Booking;
import com.skyhigh.checkin.model.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByBookingReference(String bookingReference);

    @Query("SELECT b FROM Booking b JOIN FETCH b.passenger JOIN FETCH b.flight " +
           "WHERE b.bookingReference = :bookingReference")
    Optional<Booking> findByBookingReferenceWithDetails(@Param("bookingReference") String bookingReference);

    @Query("SELECT b FROM Booking b JOIN FETCH b.passenger JOIN FETCH b.flight " +
           "WHERE b.bookingReference = :bookingReference AND LOWER(b.passenger.lastName) = LOWER(:lastName) " +
           "AND LOWER(b.passenger.email) = LOWER(:email)")
    Optional<Booking> findByBookingReferenceAndPassengerDetails(
            @Param("bookingReference") String bookingReference,
            @Param("lastName") String lastName,
            @Param("email") String email
    );

    List<Booking> findByPassengerId(UUID passengerId);

    @Query("SELECT b FROM Booking b JOIN FETCH b.flight WHERE b.passenger.id = :passengerId AND b.status = :status")
    List<Booking> findByPassengerIdAndStatus(@Param("passengerId") UUID passengerId, @Param("status") BookingStatus status);

    boolean existsByPassengerIdAndFlightIdAndStatus(UUID passengerId, UUID flightId, BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.passenger.id = :passengerId AND b.flight.id = :flightId AND b.status = 'ACTIVE'")
    Optional<Booking> findActiveBookingByPassengerAndFlight(@Param("passengerId") UUID passengerId, @Param("flightId") UUID flightId);
}

