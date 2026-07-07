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
    private final MetricsService metricsService;

    public SyncScheduler(
            PlatformConnectionRepository connectionRepository,
            PancakeConnector pancakeConnector,
            MetricsService metricsService
    ) {
        this.connectionRepository = connectionRepository;
        this.pancakeConnector = pancakeConnector;
        this.metricsService = metricsService;
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
                        pancakeConnector.syncOrders(conn.getId(), null);
                        pancakeConnector.syncAdsInsights(conn.getId(), null);
                    } catch (Exception e) {
                        System.err.println("[Scheduler] Error syncing for connection " + conn.getId() + ": " + e.getMessage());
                    }
                }
            }
            metricsService.rebuildHourlyMetrics(168);
            metricsService.rebuildDailyMetrics(7);
            metricsService.clearDashboardCache();
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
}
