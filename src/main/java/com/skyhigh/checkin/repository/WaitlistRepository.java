package com.skyhigh.checkin.repository;

import com.skyhigh.checkin.model.entity.WaitlistEntry;
import com.skyhigh.checkin.model.enums.SeatClass;
import com.skyhigh.checkin.model.enums.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {

    @Query("SELECT w FROM WaitlistEntry w WHERE w.flight.id = :flightId AND w.status = :status ORDER BY w.priority ASC")
    List<WaitlistEntry> findByFlightIdAndStatusOrderByPriority(
            @Param("flightId") UUID flightId,
            @Param("status") WaitlistStatus status);

    @Query("SELECT w FROM WaitlistEntry w WHERE w.flight.id = :flightId AND w.passenger.id = :passengerId " +
           "AND w.status IN ('WAITING', 'OFFERED')")
    Optional<WaitlistEntry> findActiveByFlightAndPassenger(
            @Param("flightId") UUID flightId,
            @Param("passengerId") UUID passengerId);

    @Query("SELECT w FROM WaitlistEntry w WHERE w.flight.id = :flightId AND w.passenger.id = :passengerId")
    Optional<WaitlistEntry> findByFlightAndPassenger(
            @Param("flightId") UUID flightId,
            @Param("passengerId") UUID passengerId);

    @Query("SELECT COUNT(w) FROM WaitlistEntry w WHERE w.flight.id = :flightId AND w.status = :status")
    long countByFlightIdAndStatus(@Param("flightId") UUID flightId, @Param("status") WaitlistStatus status);

    @Query("SELECT w FROM WaitlistEntry w WHERE w.flight.id = :flightId AND w.status = 'WAITING' " +
           "AND (w.preferredSeatClass = :seatClass OR w.preferredSeatClass IS NULL) " +
           "ORDER BY w.priority ASC")
    List<WaitlistEntry> findNextWaitingByFlightAndClass(
            @Param("flightId") UUID flightId,
            @Param("seatClass") SeatClass seatClass);

    @Query("SELECT w FROM WaitlistEntry w WHERE w.status = 'OFFERED' AND w.offerExpiresAt < :now")
    List<WaitlistEntry> findExpiredOffers(@Param("now") LocalDateTime now);

    @Query("SELECT COALESCE(MAX(w.priority), 0) FROM WaitlistEntry w WHERE w.flight.id = :flightId")
    int findMaxPriorityByFlightId(@Param("flightId") UUID flightId);

    @Query("SELECT COUNT(w) FROM WaitlistEntry w WHERE w.flight.id = :flightId " +
           "AND w.status = 'WAITING' AND w.priority < :priority")
    long countAheadInQueue(@Param("flightId") UUID flightId, @Param("priority") int priority);

    List<WaitlistEntry> findByFlightIdAndStatusIn(UUID flightId, List<WaitlistStatus> statuses);
}

