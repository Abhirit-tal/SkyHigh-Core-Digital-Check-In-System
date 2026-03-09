package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.RabbitMQConfig;
import com.skyhigh.checkin.dto.event.SeatReleasedEvent;
import com.skyhigh.checkin.dto.event.WaitlistOfferEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SeatEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public SeatEventPublisher(@Autowired(required = false) @Nullable RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        if (rabbitTemplate == null) {
            log.warn("RabbitTemplate not available — event publishing will be disabled (fallback to scheduler).");
        }
    }

    /**
     * Publishes a seat released event to trigger waitlist processing.
     */
    public void publishSeatReleased(SeatReleasedEvent event) {
        if (rabbitTemplate == null) {
            log.warn("RabbitMQ not available. Seat released event not published: flight={}, seat={}",
                    event.getFlightId(), event.getSeatNumber());
            return;
        }
        try {
            log.info("Publishing seat released event: flight={}, seat={}, reason={}",
                    event.getFlightId(), event.getSeatNumber(), event.getReason());
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.SEAT_RELEASED_ROUTING_KEY,
                    event
            );
        } catch (Exception e) {
            log.error("Failed to publish seat released event: {}", e.getMessage());
            log.warn("Will attempt synchronous waitlist processing as fallback");
        }
    }

    /**
     * Publishes a waitlist offer notification event.
     */
    public void publishWaitlistOffer(WaitlistOfferEvent event) {
        if (rabbitTemplate == null) {
            log.warn("RabbitMQ not available. Waitlist offer event not published: entry={}",
                    event.getWaitlistEntryId());
            return;
        }
        try {
            log.info("Publishing waitlist offer event: entry={}, passenger={}, seat={}",
                    event.getWaitlistEntryId(), event.getPassengerId(), event.getSeatNumber());
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    RabbitMQConfig.WAITLIST_NOTIFICATION_ROUTING_KEY,
                    event
            );
        } catch (Exception e) {
            log.error("Failed to publish waitlist offer event: {}", e.getMessage());
        }
    }
}

