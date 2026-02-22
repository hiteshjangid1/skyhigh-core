package com.skyhigh.domain.waitlist.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "waitlist_entries", indexes = {
    @Index(name = "idx_waitlist_flight_status", columnList = "flight_id, status"),
    @Index(name = "idx_waitlist_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @Column(name = "passenger_id", nullable = false, length = 100)
    private String passengerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitlistStatus status;

    @Column(name = "reservation_id")
    private Long reservationId;

    private Instant createdAt;
    private Instant assignedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
