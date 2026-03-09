package com.skyhigh.checkin.dto.response;

import com.skyhigh.checkin.model.enums.SeatClass;
import com.skyhigh.checkin.model.enums.WaitlistStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistResponse {
    private UUID entryId;
    private UUID flightId;
    private UUID passengerId;
    private WaitlistStatus status;
    private SeatClass preferredSeatClass;
    private int position;
    private long totalWaiting;
    private LocalDateTime joinedAt;
    private LocalDateTime offeredAt;
    private LocalDateTime offerExpiresAt;
    private SeatInfo offeredSeat;
    private String message;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeatInfo {
        private UUID seatId;
        private String seatNumber;
        private String seatClass;
    }
}

