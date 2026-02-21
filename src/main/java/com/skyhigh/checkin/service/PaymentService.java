package com.skyhigh.checkin.service;

import com.skyhigh.checkin.dto.response.PaymentResponse;
import com.skyhigh.checkin.exception.PaymentFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mock Payment Service - processes payments synchronously.
 * Deterministic failures for testing:
 * - Amount ending in .99 → DECLINED
 * - Amount ending in .88 → TIMEOUT (simulated)
 * - All other amounts → SUCCESS
 */
@Service
@Slf4j
public class PaymentService {

    public record PaymentResult(
            UUID paymentId,
            String status,
            BigDecimal amount,
            String currency,
            String reference,
            String declineReason,
            LocalDateTime processedAt
    ) {}

    /**
     * Processes a payment synchronously.
     * Mock implementation with deterministic failures for testing.
     *
     * @param amount        The payment amount
     * @param currency      The currency (e.g., "INR")
     * @param idempotencyKey Optional idempotency key
     * @return PaymentResult with the payment outcome
     */
    public PaymentResult processPayment(BigDecimal amount, String currency, String idempotencyKey) {
        log.info("Processing payment: amount={}, currency={}, idempotencyKey={}", amount, currency, idempotencyKey);

        UUID paymentId = UUID.randomUUID();
        String reference = "PAY-" + paymentId.toString().substring(0, 8).toUpperCase();

        // Deterministic failure scenarios for testing
        String amountStr = amount.toString();

        // Amount ending in .99 → DECLINED
        if (amountStr.endsWith(".99")) {
            log.warn("Payment declined (test scenario): amount ends in .99");
            return new PaymentResult(
                    paymentId,
                    "DECLINED",
                    amount,
                    currency,
                    reference,
                    "INSUFFICIENT_FUNDS",
                    LocalDateTime.now()
            );
        }

        // Amount ending in .88 → TIMEOUT (simulated as failure)
        if (amountStr.endsWith(".88")) {
            log.warn("Payment timeout (test scenario): amount ends in .88");
            throw new PaymentFailedException("Payment gateway timeout");
        }

        // All other amounts → SUCCESS
        log.info("Payment successful: reference={}", reference);
        return new PaymentResult(
                paymentId,
                "COMPLETED",
                amount,
                currency,
                reference,
                null,
                LocalDateTime.now()
        );
    }

    /**
     * Gets the status of a payment by reference.
     *
     * @param paymentReference The payment reference
     * @return PaymentResponse with the payment status
     */
    public PaymentResponse getPaymentStatus(String paymentReference) {
        log.info("Getting payment status for: {}", paymentReference);

        // Mock implementation - always return completed
        return PaymentResponse.builder()
                .paymentId(UUID.randomUUID())
                .status("COMPLETED")
                .reference(paymentReference)
                .processedAt(LocalDateTime.now())
                .build();
    }
}

