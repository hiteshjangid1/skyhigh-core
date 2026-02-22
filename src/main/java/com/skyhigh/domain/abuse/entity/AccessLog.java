package com.skyhigh.domain.abuse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "access_logs", indexes = {
    @Index(name = "idx_access_source_time", columnList = "source_id, accessed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false, length = 100)
    private String sourceId;

    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;

    @Column(name = "endpoint", length = 255)
    private String endpoint;
}
