package com.skyhigh.domain.seat.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "seats", indexes = {
    @Index(name = "idx_seat_flight", columnList = "flight_id"),
    @Index(name = "idx_seat_flight_number", columnList = "flight_id, seat_number", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @Column(name = "seat_number", nullable = false, length = 10)
    private String seatNumber;

    @Column(nullable = false)
    private Integer rowNumber;

    @Column(nullable = false)
    private String columnLetter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SeatState state = SeatState.AVAILABLE;

    @Version
    private Long version;
}
