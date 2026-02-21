package com.skyhigh.checkin.repository;

import com.skyhigh.checkin.model.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PassengerRepository extends JpaRepository<Passenger, UUID> {

    Optional<Passenger> findByEmail(String email);

    @Query("SELECT p FROM Passenger p WHERE LOWER(p.email) = LOWER(:email) AND LOWER(p.lastName) = LOWER(:lastName)")
    Optional<Passenger> findByEmailAndLastNameIgnoreCase(@Param("email") String email, @Param("lastName") String lastName);
}

