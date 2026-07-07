package com.mdata.backend.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "platform_tokens")
public class PlatformToken {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "connection_id")
    private UUID connectionId;

    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "expires_at")
    private Instant expiresAt;

    // In PostgreSQL scopes is text[]. In Spring Boot, we can map text[] to String[] or java.util.List<String>
    // However, to keep it simple and avoid array type mapping complexity, we can map it as a String or list with a custom column definition
    @Column(name = "scopes", columnDefinition = "text[]")
    private String[] scopes;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
