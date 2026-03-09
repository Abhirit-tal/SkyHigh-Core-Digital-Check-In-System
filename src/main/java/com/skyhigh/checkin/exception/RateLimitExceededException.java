package com.skyhigh.checkin.exception;

public class RateLimitExceededException extends SkyHighBaseException {

    public RateLimitExceededException(int retryAfterSeconds) {
        super("Rate limit exceeded. Too many requests detected. Please try again later.",
              "RATE_LIMIT_EXCEEDED", true, retryAfterSeconds);
    }
}

