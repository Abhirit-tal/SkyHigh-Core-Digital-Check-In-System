package com.skyhigh.checkin.scheduler;

import com.skyhigh.checkin.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WaitlistOfferExpiryScheduler {

    private final WaitlistService waitlistService;

    /**
     * Runs every 30 seconds to expire unclaimed waitlist offers
     * and re-offer seats to the next person in the queue.
     */
    @Scheduled(fixedRate = 30000)
    @SchedulerLock(name = "expireUnclaimedOffers", lockAtLeastFor = "10s", lockAtMostFor = "2m")
    public void expireUnclaimedOffers() {
        try {
            waitlistService.expireUnclaimedOffers();
        } catch (Exception e) {
            log.error("Error expiring unclaimed waitlist offers: {}", e.getMessage());
        }
    }
}

