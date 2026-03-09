package com.skyhigh.checkin.exception;

public class WaitlistFullException extends SkyHighBaseException {
    public WaitlistFullException(int maxCapacity) {
        super(String.format("Waitlist is full. Maximum capacity: %d", maxCapacity), "WAITLIST_FULL");
    }
}

