package com.skyhigh.checkin.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "boarding_passes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardingPass {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_in_id", nullable = false, unique = true)
    private CheckIn checkIn;

    @Column(name = "passenger_name", nullable = false, length = 100)
    private String passengerName;

    @Column(name = "flight_number", nullable = false, length = 10)
    private String flightNumber;

    @Column(name = "seat_number", nullable = false, length = 4)
    private String seatNumber;

    @Column(name = "seat_class", nullable = false, length = 20)
    private String seatClass;

    @Column(nullable = false, length = 3)
    private String origin;

    @Column(nullable = false, length = 3)
    private String destination;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(length = 10)
    private String gate;

    @Column(name = "boarding_time")
    private LocalDateTime boardingTime;

    @Column(name = "barcode_data", nullable = false, unique = true, length = 100)
    private String barcodeData;

    @Column(name = "qr_code_data", columnDefinition = "TEXT")
    private String qrCodeData;

    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private LocalDateTime generatedAt;
}

