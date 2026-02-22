package com.skyhigh.domain.abuse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "abuse_events", indexes = {
    @Index(name = "idx_abuse_source", columnList = "source_id"),
    @Index(name = "idx_abuse_time", columnList = "detected_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbuseEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false, length = 100)
    private String sourceId;

    @Column(name = "access_count")
    private Integer accessCount;

    @Column(name = "window_seconds")
    private Integer windowSeconds;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
}
