package com.mdata.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdata.backend.dto.DashboardDataDto;
import com.mdata.backend.entity.DashboardSnapshot;
import com.mdata.backend.entity.PlatformConnection;
import com.mdata.backend.repository.DashboardSnapshotRepository;
import com.mdata.backend.repository.PlatformConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardSnapshotService {
    public static final String BOOTSTRAP = "BOOTSTRAP";
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(VN_ZONE);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DashboardSnapshotRepository snapshotRepository;
    private final PlatformConnectionRepository connectionRepository;
    private final MetricsService metricsService;
    private final DashboardCacheService cacheService;

    public DashboardSnapshotService(
            DashboardSnapshotRepository snapshotRepository,
            PlatformConnectionRepository connectionRepository,
            MetricsService metricsService,
            DashboardCacheService cacheService
    ) {
        this.snapshotRepository = snapshotRepository;
        this.connectionRepository = connectionRepository;
        this.metricsService = metricsService;
        this.cacheService = cacheService;
    }

    public DashboardSnapshotResponse getBootstrapSnapshot(UUID connectionId, LocalDate date) {
        return cacheService.getBootstrap(connectionId, date)
                .map(response -> response.withSource("cache"))
                .orElseGet(() -> loadOrRebuild(connectionId, date));
    }

    public DashboardSnapshotResponse currentForShop(String shopId, LocalDate date) {
        PlatformConnection connection = connectionRepository.findByPlatform("pancake").stream()
                .filter(c -> shopId.equals(c.getShopId()))
                .findFirst()
                .orElse(null);
        if (connection == null) {
            return response(null, date, 0L, null, null, "rebuilt", metricsService.getLiveDashboardData(shopId, date.toString(), false));
        }
        return getBootstrapSnapshot(connection.getId(), date);
    }

    @Transactional
    public DashboardSnapshotResponse rebuildBootstrapSnapshot(UUID connectionId, LocalDate date) {
        PlatformConnection connection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
        DashboardDataDto payload = metricsService.getLiveDashboardData(connection.getShopId(), date.toString(), false);
        DashboardSnapshot snapshot = snapshotRepository.findByConnectionIdAndSnapshotDateAndSnapshotType(connectionId, date, BOOTSTRAP)
                .orElseGet(DashboardSnapshot::new);
        long version = snapshot.getId() == null || snapshot.getVersion() == null ? 1L : snapshot.getVersion() + 1L;
        Instant now = Instant.now();
        snapshot.setConnectionId(connectionId);
        snapshot.setSnapshotDate(date);
        snapshot.setSnapshotType(BOOTSTRAP);
        snapshot.setPayload(write(payload));
        snapshot.setVersion(version);
        snapshot.setGeneratedAt(now);
        snapshot.setLastSyncedAt(connection.getLastSuccessfulSyncAt());
        snapshot.setUpdatedAt(now);
        DashboardSnapshot saved = snapshotRepository.save(snapshot);
        DashboardSnapshotResponse response = response(connectionId, date, saved.getVersion(), saved.getGeneratedAt(), saved.getLastSyncedAt(), "rebuilt", payload);
        cacheService.putBootstrap(connectionId, date, response);
        return response;
    }

    private DashboardSnapshotResponse loadOrRebuild(UUID connectionId, LocalDate date) {
        return snapshotRepository.findByConnectionIdAndSnapshotDateAndSnapshotType(connectionId, date, BOOTSTRAP)
                .map(snapshot -> {
                    if (shouldRebuild(snapshot, date)) {
                        return rebuildBootstrapSnapshot(connectionId, date);
                    }
                    DashboardSnapshotResponse response = response(
                            connectionId,
                            date,
                            snapshot.getVersion(),
                            snapshot.getGeneratedAt(),
                            snapshot.getLastSyncedAt(),
                            "snapshot",
                            read(snapshot.getPayload())
                    );
                    cacheService.putBootstrap(connectionId, date, response);
                    return response;
                })
                .orElseGet(() -> rebuildBootstrapSnapshot(connectionId, date));
    }

    private boolean shouldRebuild(DashboardSnapshot snapshot, LocalDate date) {
        LocalDate today = LocalDate.now(VN_ZONE);
        return date.equals(today)
                && snapshot.getGeneratedAt() != null
                && snapshot.getGeneratedAt().isBefore(Instant.now().minusSeconds(30));
    }

    private String write(DashboardDataDto payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize dashboard snapshot", e);
        }
    }

    private DashboardDataDto read(String payload) {
        try {
            return objectMapper.readValue(payload, DashboardDataDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize dashboard snapshot", e);
        }
    }

    private DashboardSnapshotResponse response(UUID connectionId, LocalDate date, Long version, Instant generatedAt, Instant lastSyncedAt, String source, DashboardDataDto payload) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("connectionId", connectionId);
        meta.put("date", date.toString());
        meta.put("version", version);
        meta.put("generatedAt", generatedAt != null ? VN_OFFSET_FORMATTER.format(generatedAt) : null);
        meta.put("lastSyncedAt", lastSyncedAt != null ? VN_OFFSET_FORMATTER.format(lastSyncedAt) : null);
        meta.put("source", source);
        return new DashboardSnapshotResponse(true, payload, meta);
    }

    public record DashboardSnapshotResponse(boolean success, DashboardDataDto data, Map<String, Object> meta) {
        DashboardSnapshotResponse withSource(String source) {
            Map<String, Object> copy = new LinkedHashMap<>(meta);
            copy.put("source", source);
            return new DashboardSnapshotResponse(success, data, copy);
        }

        public DashboardSnapshotResponse withRefreshQueued(boolean refreshQueued) {
            Map<String, Object> copy = new LinkedHashMap<>(meta);
            copy.put("refreshQueued", refreshQueued);
            return new DashboardSnapshotResponse(success, data, copy);
        }
    }
}
