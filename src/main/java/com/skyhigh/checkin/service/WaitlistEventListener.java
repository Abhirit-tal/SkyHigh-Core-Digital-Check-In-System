package com.skyhigh.checkin.service;

import com.skyhigh.checkin.config.RabbitMQConfig;
import com.skyhigh.checkin.dto.event.SeatReleasedEvent;
import com.skyhigh.checkin.dto.event.WaitlistOfferEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "skyhigh.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class WaitlistEventListener {

    private final WaitlistService waitlistService;

    /**
     * Listens for seat released events and triggers waitlist processing.
     * When a seat becomes available (hold expired, cancelled, manually released),
     * it is offered to the next eligible waitlisted passenger.
     */
    @RabbitListener(queues = RabbitMQConfig.SEAT_RELEASED_QUEUE)
    public void handleSeatReleased(SeatReleasedEvent event) {
        log.info("Received seat released event: flight={}, seat={}, reason={}",
                event.getFlightId(), event.getSeatNumber(), event.getReason());

        try {
            waitlistService.offerSeatToNextInLine(event);
        } catch (Exception e) {
            log.error("Error processing seat released event for seat {} on flight {}: {}",
                    event.getSeatNumber(), event.getFlightId(), e.getMessage());
        }
    }

    /**
     * Listens for waitlist offer notification events.
     * In a production system, this would trigger email/SMS/push notifications.
     * Currently logs the notification as a stub.
     */
    @RabbitListener(queues = RabbitMQConfig.WAITLIST_NOTIFICATION_QUEUE)
    public void handleWaitlistNotification(WaitlistOfferEvent event) {
        log.info("=== WAITLIST NOTIFICATION ===");
        log.info("Passenger: {} ({})", event.getPassengerName(), event.getPassengerEmail());
        log.info("A seat has become available on your waitlisted flight!");
        log.info("Seat: {} (Class: {})", event.getSeatNumber(), event.getSeatClass());
        log.info("Offer expires at: {}", event.getOfferExpiresAt());
        log.info("Accept via: POST /api/v1/waitlist/{}/accept", event.getWaitlistEntryId());
        log.info("=============================");

        // In production: send email/SMS/push notification here
        // emailService.sendWaitlistOfferEmail(event);
        // smsService.sendWaitlistOfferSms(event);
    }
}

