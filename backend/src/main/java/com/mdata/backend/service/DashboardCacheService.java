package com.mdata.backend.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DashboardCacheService {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public Optional<DashboardSnapshotService.DashboardSnapshotResponse> getBootstrap(UUID connectionId, LocalDate date) {
        String key = key(connectionId, date);
        CacheEntry entry = cache.get(key);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt()) {
            cache.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public void putBootstrap(UUID connectionId, LocalDate date, DashboardSnapshotService.DashboardSnapshotResponse value) {
        cache.put(key(connectionId, date), new CacheEntry(value, System.currentTimeMillis() + ttl(date).toMillis()));
    }

    public void evictDate(UUID connectionId, LocalDate date) {
        cache.remove(key(connectionId, date));
    }

    public void clearAll() {
        cache.clear();
    }

    private Duration ttl(LocalDate date) {
        LocalDate today = LocalDate.now(VN_ZONE);
        if (date.equals(today)) return Duration.ofSeconds(10);
        if (date.equals(today.minusDays(1))) return Duration.ofMinutes(15);
        return Duration.ofHours(1);
    }

    private String key(UUID connectionId, LocalDate date) {
        return "dashboard:bootstrap:" + connectionId + ":" + date;
    }

    private record CacheEntry(DashboardSnapshotService.DashboardSnapshotResponse value, long expiresAt) {}
}
