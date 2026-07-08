package com.mdata.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class OrderIngestionResult {
    private final UUID connectionId;
    private final Set<LocalDate> affectedDates = new HashSet<>();
    private final List<UUID> confirmedOrderIds = new ArrayList<>();
    private int processedCount;
    private int ignoredCount;
    private int failedCount;

    public OrderIngestionResult(UUID connectionId) {
        this.connectionId = connectionId;
    }

    public UUID getConnectionId() {
        return connectionId;
    }

    public Set<LocalDate> getAffectedDates() {
        return affectedDates;
    }

    public List<UUID> getConfirmedOrderIds() {
        return confirmedOrderIds;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public void incrementProcessedCount() {
        processedCount++;
    }

    public int getIgnoredCount() {
        return ignoredCount;
    }

    public void incrementIgnoredCount() {
        ignoredCount++;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void incrementFailedCount() {
        failedCount++;
    }
}
