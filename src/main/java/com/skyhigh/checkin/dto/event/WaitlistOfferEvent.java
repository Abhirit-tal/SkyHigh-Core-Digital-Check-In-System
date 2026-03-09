package com.skyhigh.checkin.dto.event;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistOfferEvent implements Serializable {
    private UUID waitlistEntryId;
    private UUID flightId;
    private UUID passengerId;
    private UUID seatId;
    private String seatNumber;
    private String seatClass;
    private LocalDateTime offerExpiresAt;
    private String passengerEmail;
    private String passengerName;
}

