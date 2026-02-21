package com.skyhigh.checkin.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class SeatAlreadyConfirmedException extends SkyHighBaseException {

    private final UUID flightId;
    private final String seatNumber;

    public SeatAlreadyConfirmedException(UUID flightId, String seatNumber) {
        super("Seat " + seatNumber + " has been permanently assigned to another passenger",
              "SEAT_ALREADY_CONFIRMED", false);
        this.flightId = flightId;
        this.seatNumber = seatNumber;
    }
}

