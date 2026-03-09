package com.skyhigh.checkin.service;

import com.skyhigh.checkin.dto.event.SeatReleasedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles seat released domain events AFTER the database transaction commits.
 * This guarantees that:
 * 1. The DB state (seat → AVAILABLE) is fully committed before RabbitMQ publish.
 * 2. If the transaction rolls back, no ghost event is sent to the message broker.
 * 3. If RabbitMQ is down, the DB change is still persisted (eventual consistency via scheduler fallback).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionalSeatEventHandler {

    private final SeatEventPublisher seatEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSeatReleasedAfterCommit(SeatReleasedEvent event) {
        log.info("Transaction committed — publishing seat released event to RabbitMQ: flight={}, seat={}, reason={}",
                event.getFlightId(), event.getSeatNumber(), event.getReason());
        try {
            seatEventPublisher.publishSeatReleased(event);
        } catch (Exception e) {
            // RabbitMQ publish failure after DB commit: logged, scheduler will pick up the seat as fallback
            log.error("Failed to publish seat released event after commit: {}. Scheduler fallback will handle waitlist.",
                    e.getMessage());
        }
    }
}

