package com.mdata.backend.service;

import com.mdata.backend.connector.PancakeConnector;
import com.mdata.backend.entity.PlatformConnection;
import com.mdata.backend.repository.PlatformConnectionRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SyncScheduler {

    private final PlatformConnectionRepository connectionRepository;
    private final PancakeConnector pancakeConnector;

    public SyncScheduler(
            PlatformConnectionRepository connectionRepository,
            PancakeConnector pancakeConnector
    ) {
        this.connectionRepository = connectionRepository;
        this.pancakeConnector = pancakeConnector;
    }

    // Run automatically every 10 minutes to sync Pancake POS orders and ads
    @Scheduled(cron = "0 */10 * * * *")
    public void autoSync() {
        System.out.println("[Scheduler] Starting automatic background sync for Pancake POS...");
        try {
            List<PlatformConnection> connections = connectionRepository.findByPlatform("pancake");
            for (PlatformConnection conn : connections) {
                if ("active".equals(conn.getStatus())) {
                    try {
                        java.time.Instant since = conn.getLastSuccessfulSyncAt();
                        pancakeConnector.syncOrders(conn.getId(), since);
                        pancakeConnector.syncAdsInsights(conn.getId(), since);
                        conn.setLastSuccessfulSyncAt(java.time.Instant.now());
                        conn.setLastErrorAt(null);
                        conn.setLastErrorMessage(null);
                        conn.setUpdatedAt(java.time.Instant.now());
                        connectionRepository.save(conn);
                    } catch (Exception e) {
                        conn.setLastErrorAt(java.time.Instant.now());
                        conn.setLastErrorMessage(truncate(e.getMessage(), 250));
                        conn.setUpdatedAt(java.time.Instant.now());
                        connectionRepository.save(conn);
                        System.err.println("[Scheduler] Error syncing for connection " + conn.getId() + ": " + e.getMessage());
                    }
                }
            }
            System.out.println("[Scheduler] Automatic background sync completed successfully.");
        } catch (Exception e) {
            System.err.println("[Scheduler] Global error in background sync: " + e.getMessage());
        }
    }

    // Run once on application startup when the system is ready
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        System.out.println("[Scheduler] Running initial sync on application startup...");
        autoSync();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
