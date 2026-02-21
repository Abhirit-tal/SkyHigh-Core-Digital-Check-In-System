package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.CheckInConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WeightServiceTest {

    private WeightService weightService;
    private CheckInConfig checkInConfig;

    @BeforeEach
    void setUp() {
        checkInConfig = new CheckInConfig();
        checkInConfig.setMaxBaggageWeightKg(BigDecimal.valueOf(25));
        checkInConfig.setExcessBaggageFeePerKg(BigDecimal.valueOf(200));

        weightService = new WeightService(checkInConfig);
    }

    @Test
    void validateWeight_ShouldReturnWithinLimit_WhenWeightIsUnderLimit() {
        // Given
        BigDecimal weight = BigDecimal.valueOf(20);

        // When
        WeightService.WeightValidationResult result = weightService.validateWeight(weight);

        // Then
        assertTrue(result.withinLimit());
        assertEquals(BigDecimal.ZERO, result.excessKg());
        assertEquals(BigDecimal.ZERO, result.excessFee());
    }

    @Test
    void validateWeight_ShouldReturnWithinLimit_WhenWeightEqualsLimit() {
        // Given
        BigDecimal weight = BigDecimal.valueOf(25);

        // When
        WeightService.WeightValidationResult result = weightService.validateWeight(weight);

        // Then
        assertTrue(result.withinLimit());
        assertEquals(BigDecimal.ZERO, result.excessKg());
    }

    @Test
    void validateWeight_ShouldCalculateExcessFee_WhenWeightExceedsLimit() {
        // Given
        BigDecimal weight = BigDecimal.valueOf(30);

        // When
        WeightService.WeightValidationResult result = weightService.validateWeight(weight);

        // Then
        assertFalse(result.withinLimit());
        assertEquals(BigDecimal.valueOf(5), result.excessKg());
        assertEquals(BigDecimal.valueOf(1000), result.excessFee()); // 5kg * 200 = 1000
        assertEquals("INR", result.currency());
    }

    @Test
    void validateWeight_ShouldHandleDecimalWeights() {
        // Given
        BigDecimal weight = new BigDecimal("30.5");

        // When
        WeightService.WeightValidationResult result = weightService.validateWeight(weight);

        // Then
        assertFalse(result.withinLimit());
        assertEquals(new BigDecimal("5.5"), result.excessKg());
        assertEquals(new BigDecimal("1100.0"), result.excessFee()); // 5.5kg * 200 = 1100
    }
}

