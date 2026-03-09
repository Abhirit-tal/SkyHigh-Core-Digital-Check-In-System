package com.skyhigh.checkin.model.entity;

import com.skyhigh.checkin.model.enums.SeatClass;
import com.skyhigh.checkin.model.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "seats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"flight_id", "seat_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(name = "seat_number", nullable = false, length = 4)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_class", nullable = false, length = 20)
    private SeatClass seatClass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SeatStatus status = SeatStatus.AVAILABLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "held_by_passenger_id")
    private Passenger heldByPassenger;

    @Column(name = "held_until")
    private LocalDateTime heldUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_passenger_id")
    private Passenger confirmedByPassenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_passenger_id")
    private Passenger cancelledByPassenger;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    public boolean isHeld() {
        return status == SeatStatus.HELD;
    }

    public boolean isConfirmed() {
        return status == SeatStatus.CONFIRMED;
    }

    public boolean isCancelled() {
        return status == SeatStatus.CANCELLED;
    }

    public boolean isHeldByPassenger(UUID passengerId) {
        return isHeld() && heldByPassenger != null && heldByPassenger.getId().equals(passengerId);
    }

    public boolean isConfirmedByPassenger(UUID passengerId) {
        return isConfirmed() && confirmedByPassenger != null && confirmedByPassenger.getId().equals(passengerId);
    }

    public boolean isHoldExpired() {
        return isHeld() && heldUntil != null && LocalDateTime.now().isAfter(heldUntil);
    }

    /**
     * Resets the seat to AVAILABLE state, clearing all hold/confirm/cancel fields.
     */
    public void resetToAvailable() {
        this.status = SeatStatus.AVAILABLE;
        this.heldByPassenger = null;
        this.heldUntil = null;
        this.confirmedByPassenger = null;
        this.cancelledByPassenger = null;
        this.cancelledAt = null;
    }
}

