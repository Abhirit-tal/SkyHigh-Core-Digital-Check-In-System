package com.skyhigh.checkin.service;

import com.skyhigh.checkin.dto.event.SeatReleasedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Publishes seat-related domain events via Spring's ApplicationEventPublisher.
 * Events are published within the transaction scope but handled AFTER commit
 * by {@link TransactionalSeatEventHandler}, ensuring the DB state is consistent
 * before the RabbitMQ message is sent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SeatDomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Publish a domain event that will be handled after the current transaction commits.
     */
    public void publishAfterCommit(SeatReleasedEvent event) {
        log.debug("Registering post-commit seat released event: flight={}, seat={}, reason={}",
                event.getFlightId(), event.getSeatNumber(), event.getReason());
        applicationEventPublisher.publishEvent(event);
    }
}

