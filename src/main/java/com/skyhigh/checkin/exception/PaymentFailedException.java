package com.skyhigh.checkin.exception;

public class PaymentFailedException extends SkyHighBaseException {

    public PaymentFailedException(String reason) {
        super("Payment processing failed: " + reason, "PAYMENT_FAILED", true);
    }
}

