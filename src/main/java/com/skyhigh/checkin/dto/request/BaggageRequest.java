package com.skyhigh.checkin.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaggageRequest {

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.0", message = "Weight must be non-negative")
    private BigDecimal weightKg;
}

