package com.skyhigh.checkin.repository;

import com.skyhigh.checkin.model.entity.BoardingPass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoardingPassRepository extends JpaRepository<BoardingPass, UUID> {

    Optional<BoardingPass> findByCheckInId(UUID checkInId);

    Optional<BoardingPass> findByBarcodeData(String barcodeData);

    @Query("SELECT bp FROM BoardingPass bp JOIN FETCH bp.checkIn c JOIN FETCH c.booking b " +
           "JOIN FETCH b.passenger WHERE bp.id = :id")
    Optional<BoardingPass> findByIdWithDetails(@Param("id") UUID id);

    boolean existsByCheckInId(UUID checkInId);
}

