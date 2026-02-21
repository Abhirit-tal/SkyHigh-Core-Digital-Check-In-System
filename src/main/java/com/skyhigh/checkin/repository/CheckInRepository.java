package com.skyhigh.checkin.repository;

import com.skyhigh.checkin.model.entity.CheckIn;
import com.skyhigh.checkin.model.enums.CheckInStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

    @Query("SELECT c FROM CheckIn c JOIN FETCH c.booking b JOIN FETCH b.passenger JOIN FETCH b.flight WHERE c.id = :id")
    Optional<CheckIn> findByIdWithDetails(@Param("id") UUID id);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT c FROM CheckIn c WHERE c.id = :id")
    Optional<CheckIn> findByIdWithLock(@Param("id") UUID id);

    @Query("SELECT c FROM CheckIn c WHERE c.booking.id = :bookingId AND c.status IN ('IN_PROGRESS', 'WAITING_PAYMENT')")
    Optional<CheckIn> findActiveCheckInByBookingId(@Param("bookingId") UUID bookingId);

    @Query("SELECT c FROM CheckIn c WHERE c.booking.passenger.id = :passengerId AND c.booking.flight.id = :flightId " +
           "AND c.status IN ('IN_PROGRESS', 'WAITING_PAYMENT')")
    Optional<CheckIn> findActiveCheckInByPassengerAndFlight(@Param("passengerId") UUID passengerId, @Param("flightId") UUID flightId);

    @Query("SELECT c FROM CheckIn c WHERE c.status IN ('IN_PROGRESS', 'WAITING_PAYMENT') AND c.expiresAt < :now")
    List<CheckIn> findExpiredSessions(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE CheckIn c SET c.status = 'EXPIRED', c.version = c.version + 1 " +
           "WHERE c.status IN ('IN_PROGRESS', 'WAITING_PAYMENT') AND c.expiresAt < :now")
    int expireSessions(@Param("now") LocalDateTime now);

    List<CheckIn> findByStatus(CheckInStatus status);

    @Query("SELECT c FROM CheckIn c WHERE c.booking.passenger.id = :passengerId ORDER BY c.createdAt DESC")
    List<CheckIn> findByPassengerId(@Param("passengerId") UUID passengerId);

    @Query("SELECT c FROM CheckIn c WHERE c.booking.flight.id = :flightId AND c.status = 'COMPLETED'")
    List<CheckIn> findCompletedCheckInsByFlightId(@Param("flightId") UUID flightId);
}

