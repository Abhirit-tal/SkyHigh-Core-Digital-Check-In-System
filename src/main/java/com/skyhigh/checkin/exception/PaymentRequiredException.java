package com.skyhigh.checkin.exception;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class PaymentRequiredException extends SkyHighBaseException {

    private final BigDecimal amount;
    private final String currency;

    public PaymentRequiredException(BigDecimal amount) {
        super("Payment is required to complete check-in. Amount due: ₹" + amount, "PAYMENT_REQUIRED", true);
        this.amount = amount;
        this.currency = "INR";
    }
}

