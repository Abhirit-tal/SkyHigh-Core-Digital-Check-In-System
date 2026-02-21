package com.skyhigh.checkin.exception;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class SeatHoldExpiredException extends SkyHighBaseException {

    private final String seatNumber;
    private final LocalDateTime expiredAt;

    public SeatHoldExpiredException(String seatNumber, LocalDateTime expiredAt) {
        super("Your seat reservation has expired. Seat " + seatNumber + " hold expired at " + expiredAt,
              "SEAT_HOLD_EXPIRED", true, 0);
        this.seatNumber = seatNumber;
        this.expiredAt = expiredAt;
    }
}

