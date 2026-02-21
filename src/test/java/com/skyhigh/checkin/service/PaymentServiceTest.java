package com.skyhigh.checkin.service;

import com.skyhigh.checkin.exception.PaymentFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService();
    }

    @Test
    void processPayment_ShouldSucceed_WhenAmountIsNormal() {
        // Given
        BigDecimal amount = BigDecimal.valueOf(1000);

        // When
        PaymentService.PaymentResult result = paymentService.processPayment(amount, "INR", null);

        // Then
        assertEquals("COMPLETED", result.status());
        assertNotNull(result.paymentId());
        assertNotNull(result.reference());
        assertTrue(result.reference().startsWith("PAY-"));
        assertNull(result.declineReason());
    }

    @Test
    void processPayment_ShouldDecline_WhenAmountEndsIn99() {
        // Given
        BigDecimal amount = new BigDecimal("1099.99");

        // When
        PaymentService.PaymentResult result = paymentService.processPayment(amount, "INR", null);

        // Then
        assertEquals("DECLINED", result.status());
        assertEquals("INSUFFICIENT_FUNDS", result.declineReason());
    }

    @Test
    void processPayment_ShouldTimeout_WhenAmountEndsIn88() {
        // Given
        BigDecimal amount = new BigDecimal("1099.88");

        // When & Then
        assertThrows(PaymentFailedException.class, () ->
            paymentService.processPayment(amount, "INR", null)
        );
    }

    @Test
    void processPayment_ShouldSucceed_WithIdempotencyKey() {
        // Given
        BigDecimal amount = BigDecimal.valueOf(500);
        String idempotencyKey = "test-key-123";

        // When
        PaymentService.PaymentResult result = paymentService.processPayment(amount, "INR", idempotencyKey);

        // Then
        assertEquals("COMPLETED", result.status());
    }
}

