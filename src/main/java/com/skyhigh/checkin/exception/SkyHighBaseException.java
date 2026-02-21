package com.skyhigh.checkin.exception;

import lombok.Getter;

@Getter
public abstract class SkyHighBaseException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;
    private final Integer retryAfterSeconds;

    protected SkyHighBaseException(String message, String errorCode, boolean retryable, Integer retryAfterSeconds) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    protected SkyHighBaseException(String message, String errorCode, boolean retryable) {
        this(message, errorCode, retryable, null);
    }

    protected SkyHighBaseException(String message, String errorCode) {
        this(message, errorCode, false, null);
    }
}

