package com.skyhigh.checkin.exception;

public class InvalidCredentialsException extends SkyHighBaseException {

    public InvalidCredentialsException() {
        super("Invalid booking reference, last name, or email", "INVALID_CREDENTIALS", false);
    }

    public InvalidCredentialsException(String message) {
        super(message, "INVALID_CREDENTIALS", false);
    }
}

