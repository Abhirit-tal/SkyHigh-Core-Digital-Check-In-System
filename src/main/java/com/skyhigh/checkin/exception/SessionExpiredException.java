package com.skyhigh.checkin.exception;

public class SessionExpiredException extends SkyHighBaseException {

    public SessionExpiredException() {
        super("Your check-in session has expired. Please start a new check-in.", "SESSION_EXPIRED", true);
    }
}

