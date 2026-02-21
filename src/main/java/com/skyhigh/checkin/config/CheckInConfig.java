package com.skyhigh.checkin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "skyhigh.checkin")
@Getter
@Setter
public class CheckInConfig {

    private int seatHoldDurationSeconds = 120;
    private int sessionTimeoutMinutes = 10;
    private BigDecimal maxBaggageWeightKg = BigDecimal.valueOf(25);
    private BigDecimal excessBaggageFeePerKg = BigDecimal.valueOf(200);
    private int checkinWindowOpensHours = 24;
    private int checkinWindowClosesHours = 1;
}

