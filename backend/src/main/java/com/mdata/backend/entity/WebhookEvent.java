package com.mdata.backend.entity;

import lombok.Data;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "webhook_events", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"platform", "event_id"})
})
public class WebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String platform;

    @Column(name = "connection_id")
    private UUID connectionId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "platform_object_id")
    private String platformObjectId;

    private String status = "pending";

    @Column(name = "payload_hash")
    private String payloadHash;

    @Column(name = "attempt_count")
    private Integer attemptCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by")
    private String lockedBy;

    @Column(name = "event_updated_at_platform")
    private Instant eventUpdatedAtPlatform;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(name = "received_at")
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message")
    private String errorMessage;
}
