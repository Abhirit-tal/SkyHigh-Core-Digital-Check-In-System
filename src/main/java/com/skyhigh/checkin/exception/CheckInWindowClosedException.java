package com.skyhigh.checkin.exception;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CheckInWindowClosedException extends SkyHighBaseException {

    private final LocalDateTime closedAt;

    public CheckInWindowClosedException(LocalDateTime closedAt) {
        super("Online check-in has closed for this flight. Closed at " + closedAt,
              "CHECK_IN_CLOSED", false);
        this.closedAt = closedAt;
    }
}

