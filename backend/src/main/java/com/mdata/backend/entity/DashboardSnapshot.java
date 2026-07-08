package com.mdata.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "dashboard_snapshots", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"connection_id", "snapshot_date", "snapshot_type"})
})
public class DashboardSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "connection_id")
    private UUID connectionId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "snapshot_type", nullable = false)
    private String snapshotType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private Long version = 1L;

    @Column(name = "generated_at")
    private Instant generatedAt = Instant.now();

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
