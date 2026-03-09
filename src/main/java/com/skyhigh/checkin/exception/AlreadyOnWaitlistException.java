package com.skyhigh.checkin.exception;

import java.util.UUID;

public class AlreadyOnWaitlistException extends SkyHighBaseException {
    public AlreadyOnWaitlistException(UUID flightId, UUID passengerId) {
        super(String.format("Passenger %s is already on the waitlist for flight %s", passengerId, flightId),
              "ALREADY_ON_WAITLIST");
    }
}

