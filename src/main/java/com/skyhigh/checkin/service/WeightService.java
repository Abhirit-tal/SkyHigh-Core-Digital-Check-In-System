package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
import com.skyhigh.checkin.dto.response.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mock Weight Service - validates baggage weight and calculates excess fees.
 * This is a mock implementation that always succeeds instantly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeightService {

    private final CheckInConfig checkInConfig;

    public record WeightValidationResult(
            BigDecimal weightKg,
            BigDecimal maxAllowedKg,
            BigDecimal excessKg,
            BigDecimal excessFee,
            String currency,
            BigDecimal feePerKg,
            boolean withinLimit
    ) {}

    /**
     * Validates baggage weight and calculates excess fee if applicable.
     * Always succeeds instantly (mock implementation).
     *
     * @param weightKg The baggage weight in kilograms
     * @return WeightValidationResult with fee calculation
     */
    public WeightValidationResult validateWeight(BigDecimal weightKg) {
        log.info("Validating baggage weight: {} kg", weightKg);

        BigDecimal maxWeight = checkInConfig.getMaxBaggageWeightKg();
        BigDecimal feePerKg = checkInConfig.getExcessBaggageFeePerKg();

        boolean withinLimit = weightKg.compareTo(maxWeight) <= 0;
        BigDecimal excessKg = withinLimit ? BigDecimal.ZERO : weightKg.subtract(maxWeight);
        BigDecimal excessFee = excessKg.multiply(feePerKg);

        log.info("Weight validation result: withinLimit={}, excessKg={}, excessFee={}",
                withinLimit, excessKg, excessFee);

        return new WeightValidationResult(
                weightKg,
                maxWeight,
                excessKg,
                excessFee,
                "INR",
                feePerKg,
                withinLimit
        );
    }
}

