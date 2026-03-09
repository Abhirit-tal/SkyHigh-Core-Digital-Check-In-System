package com.skyhigh.checkin.dto.event;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatReleasedEvent implements Serializable {
    private UUID flightId;
    private UUID seatId;
    private String seatNumber;
    private String seatClass;
    private String reason; // HOLD_EXPIRED, CANCELLED, MANUAL_RELEASE
    private UUID previousHolderId;
    private LocalDateTime releasedAt;
}

