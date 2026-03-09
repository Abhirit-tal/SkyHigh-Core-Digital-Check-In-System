package com.skyhigh.checkin.dto.request;

import com.skyhigh.checkin.model.enums.SeatClass;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinWaitlistRequest {
    @NotNull(message = "Flight ID is required")
    private UUID flightId;

    private SeatClass preferredSeatClass;
}
