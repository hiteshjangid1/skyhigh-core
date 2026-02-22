package com.skyhigh.domain.seat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "seat_reservations", indexes = {
    @Index(name = "idx_reservation_seat", columnList = "seat_id"),
    @Index(name = "idx_reservation_held_until", columnList = "held_until"),
    @Index(name = "idx_reservation_passenger", columnList = "passenger_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @Column(name = "passenger_id", nullable = false, length = 100)
    private String passengerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatState state;

    @Column(name = "held_at")
    private Instant heldAt;

    @Column(name = "held_until")
    private Instant heldUntil;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
