package com.skyhigh.checkin.exception;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class BaggageWeightExceededException extends SkyHighBaseException {

    private final BigDecimal actualWeight;
    private final BigDecimal maxWeight;
    private final BigDecimal excessWeight;
    private final BigDecimal fee;

    public BaggageWeightExceededException(BigDecimal actualWeight, BigDecimal maxWeight, BigDecimal fee) {
        super("Baggage weight exceeds the allowed limit", "BAGGAGE_EXCEEDED", false);
        this.actualWeight = actualWeight;
        this.maxWeight = maxWeight;
        this.excessWeight = actualWeight.subtract(maxWeight);
        this.fee = fee;
    }
}

