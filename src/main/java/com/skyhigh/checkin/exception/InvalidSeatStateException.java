package com.skyhigh.checkin.exception;

public class InvalidSeatStateException extends SkyHighBaseException {

    public InvalidSeatStateException(String message) {
        super(message, "INVALID_SEAT_STATE", false);
    }
}

