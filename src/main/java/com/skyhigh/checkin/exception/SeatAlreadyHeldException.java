package com.skyhigh.checkin.exception;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class SeatAlreadyHeldException extends SkyHighBaseException {

    private final UUID flightId;
    private final String seatNumber;
    private final LocalDateTime heldUntil;

    public SeatAlreadyHeldException(UUID flightId, String seatNumber, LocalDateTime heldUntil) {
        super("Seat " + seatNumber + " is currently held by another passenger",
              "SEAT_ALREADY_HELD", true, calculateRetrySeconds(heldUntil));
        this.flightId = flightId;
        this.seatNumber = seatNumber;
        this.heldUntil = heldUntil;
    }

    private static Integer calculateRetrySeconds(LocalDateTime heldUntil) {
        if (heldUntil == null) return 120;
        long seconds = java.time.Duration.between(LocalDateTime.now(), heldUntil).getSeconds();
        return (int) Math.max(1, seconds);
    }
}

