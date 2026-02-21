package com.skyhigh.checkin.repository;

import com.skyhigh.checkin.model.entity.Seat;
import com.skyhigh.checkin.model.enums.SeatClass;
import com.skyhigh.checkin.model.enums.SeatStatus;
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
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findByFlightId(UUID flightId);

    List<Seat> findByFlightIdAndStatus(UUID flightId, SeatStatus status);

    List<Seat> findByFlightIdAndSeatClass(UUID flightId, SeatClass seatClass);

    Optional<Seat> findByFlightIdAndSeatNumber(UUID flightId, String seatNumber);

    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT s FROM Seat s WHERE s.id = :seatId")
    Optional<Seat> findByIdWithLock(@Param("seatId") UUID seatId);

    @Query("SELECT s FROM Seat s WHERE s.status = 'HELD' AND s.heldUntil < :now")
    List<Seat> findExpiredHolds(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Seat s SET s.status = 'AVAILABLE', s.heldByPassenger = null, s.heldUntil = null, s.version = s.version + 1 " +
           "WHERE s.status = 'HELD' AND s.heldUntil < :now")
    int releaseExpiredHolds(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.flight.id = :flightId AND s.status = :status")
    long countByFlightIdAndStatus(@Param("flightId") UUID flightId, @Param("status") SeatStatus status);

    @Query("SELECT s FROM Seat s WHERE s.flight.id = :flightId ORDER BY s.seatClass, s.seatNumber")
    List<Seat> findByFlightIdOrderBySeatClassAndNumber(@Param("flightId") UUID flightId);
}

