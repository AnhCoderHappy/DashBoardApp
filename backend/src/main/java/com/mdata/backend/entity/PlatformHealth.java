package com.mdata.backend.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.Instant;

@Data
@Entity
@Table(name = "platform_health")
public class PlatformHealth {
    @Id
    private String platform;

    @Column(nullable = false)
    private String status = "unknown";

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    @Column(name = "last_error_message")
    private String lastErrorMessage;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
