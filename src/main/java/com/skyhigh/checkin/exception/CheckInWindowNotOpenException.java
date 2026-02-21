package com.skyhigh.checkin.exception;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CheckInWindowNotOpenException extends SkyHighBaseException {

    private final LocalDateTime opensAt;

    public CheckInWindowNotOpenException(LocalDateTime opensAt) {
        super("Check-in is not yet available for this flight. Opens at " + opensAt,
              "CHECK_IN_NOT_OPEN", true, calculateSecondsUntilOpen(opensAt));
        this.opensAt = opensAt;
    }

    private static Integer calculateSecondsUntilOpen(LocalDateTime opensAt) {
        long seconds = java.time.Duration.between(LocalDateTime.now(), opensAt).getSeconds();
        return (int) Math.max(1, seconds);
    }

    public int getSecondsUntilOpen() {
        return calculateSecondsUntilOpen(opensAt);
    }
}

