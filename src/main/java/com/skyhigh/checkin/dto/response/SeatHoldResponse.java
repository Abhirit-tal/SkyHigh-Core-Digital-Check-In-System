package com.skyhigh.checkin.dto.response;

import com.skyhigh.checkin.model.enums.SeatClass;
import com.skyhigh.checkin.model.enums.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatHoldResponse {

    private UUID seatId;
    private String seatNumber;
    private SeatClass seatClass;
    private SeatStatus status;
    private LocalDateTime heldUntil;
    private int holdDurationSeconds;
}

