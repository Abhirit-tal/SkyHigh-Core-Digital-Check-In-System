package com.skyhigh.checkin.dto.response;

import com.skyhigh.checkin.model.enums.SeatClass;
import com.skyhigh.checkin.model.enums.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMapResponse {

    private UUID flightId;
    private String flightNumber;
    private Map<SeatClass, List<SeatInfo>> seatsByClass;
    private SeatSummary summary;
    private LocalDateTime retrievedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatInfo {
        private UUID id;
        private String seatNumber;
        private SeatClass seatClass;
        private SeatStatus status;
        private boolean available;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatSummary {
        private long total;
        private long available;
        private long held;
        private long confirmed;
        private Map<SeatClass, Long> availableByClass;
    }
}

