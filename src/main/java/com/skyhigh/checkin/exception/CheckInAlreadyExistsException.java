package com.skyhigh.checkin.exception;

import lombok.Getter;

import java.util.UUID;

@Getter
public class CheckInAlreadyExistsException extends SkyHighBaseException {

    private final UUID existingCheckInId;

    public CheckInAlreadyExistsException(UUID existingCheckInId) {
        super("An active check-in session already exists for this booking", "CHECK_IN_ALREADY_EXISTS", false);
        this.existingCheckInId = existingCheckInId;
    }
}

