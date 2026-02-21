package com.skyhigh.checkin.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class FlightAccessDeniedException extends SkyHighBaseException {

    private final UUID passengerId;
    private final UUID flightId;

    public FlightAccessDeniedException(UUID passengerId, UUID flightId) {
        super("You do not have a valid ticket for this flight", "FLIGHT_ACCESS_DENIED", false);
        this.passengerId = passengerId;
        this.flightId = flightId;
    }
}

