package com.skyhigh.checkin.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "abuse_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbuseAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_identifier", nullable = false)
    private String sourceIdentifier;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "endpoint")
    private String endpoint;

    @Column(name = "request_count")
    private Integer requestCount;

    @Column(name = "window_seconds")
    private Integer windowSeconds;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "blocked_until")
    private LocalDateTime blockedUntil;

    @Column(name = "passenger_id")
    private UUID passengerId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

