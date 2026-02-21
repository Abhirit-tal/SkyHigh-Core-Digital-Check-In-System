package com.skyhigh.checkin.repository;

import com.skyhigh.checkin.model.entity.SeatAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SeatAuditLogRepository extends JpaRepository<SeatAuditLog, UUID> {

    List<SeatAuditLog> findBySeatIdOrderByCreatedAtDesc(UUID seatId);

    List<SeatAuditLog> findByFlightIdOrderByCreatedAtDesc(UUID flightId);
}

