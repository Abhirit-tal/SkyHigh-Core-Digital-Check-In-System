package com.skyhigh.checkin.repository;

import com.skyhigh.checkin.model.entity.Flight;
import com.skyhigh.checkin.model.enums.FlightStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlightRepository extends JpaRepository<Flight, UUID> {

    Optional<Flight> findByFlightNumberAndDepartureTime(String flightNumber, LocalDateTime departureTime);

    List<Flight> findByStatus(FlightStatus status);

    @Query("SELECT f FROM Flight f WHERE f.departureTime BETWEEN :start AND :end AND f.status = :status")
    List<Flight> findFlightsByDepartureTimeAndStatus(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") FlightStatus status
    );

    @Query("SELECT f FROM Flight f WHERE f.departureTime > :now AND f.status = 'SCHEDULED' ORDER BY f.departureTime")
    List<Flight> findUpcomingFlights(@Param("now") LocalDateTime now);
}
