package com.skyhigh.checkin.exception;

public class BookingNotActiveException extends SkyHighBaseException {

    public BookingNotActiveException(String bookingReference) {
        super("Booking " + bookingReference + " is no longer active", "BOOKING_NOT_ACTIVE", false);
    }
}

