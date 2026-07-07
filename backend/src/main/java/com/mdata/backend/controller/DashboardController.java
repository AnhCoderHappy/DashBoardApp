package com.mdata.backend.controller;

import com.mdata.backend.dto.DashboardDataDto;
import com.mdata.backend.repository.PlatformConnectionRepository;
import com.mdata.backend.service.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping
public class DashboardController {

    private final MetricsService metricsService;
    private final PlatformConnectionRepository connectionRepository;
    private final com.mdata.backend.connector.PancakeConnector pancakeConnector;
    private final com.mdata.backend.service.SseService sseService;
    private final long startTime = System.currentTimeMillis();

    public DashboardController(
            MetricsService metricsService, 
            PlatformConnectionRepository connectionRepository,
            com.mdata.backend.connector.PancakeConnector pancakeConnector,
            com.mdata.backend.service.SseService sseService
    ) {
        this.metricsService = metricsService;
        this.connectionRepository = connectionRepository;
        this.pancakeConnector = pancakeConnector;
        this.sseService = sseService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> report = new HashMap<>();
        boolean dbOk = true;
        String details = null;

        try {
            // Check db connection using count
            connectionRepository.count();
        } catch (Exception e) {
            dbOk = false;
            details = e.getMessage();
        }

        report.put("ok", dbOk);
        report.put("service", "mdata-backend");
        report.put("timestamp", Instant.now().toString());
        report.put("uptime", (System.currentTimeMillis() - startTime) / 1000.0);
        report.put("database", dbOk ? "ok" : "error");
        if (details != null) {
            report.put("details", details);
        }

        return ResponseEntity.status(dbOk ? 200 : 500).body(report);
    }

    @GetMapping("/api/dashboard/live")
    public ResponseEntity<DashboardDataDto> getLiveDashboard(
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "all") String shopId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String date,
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "false") boolean refresh) {
        long start = System.currentTimeMillis();
        System.out.println("[PERF-MEASURE] GET /api/dashboard/live request received for shopId: " + shopId + ", date: " + date + " at: " + start + " ms");
        
        if (refresh) {
            System.out.println("[Dashboard API] Force refresh requested. Running real-time sync before returning data...");
            try {
                var connections = connectionRepository.findByPlatform("pancake");
                for (var conn : connections) {
                    if ("active".equals(conn.getStatus())) {
                        pancakeConnector.syncOrders(conn.getId(), null);
                        pancakeConnector.syncAdsInsights(conn.getId(), null);
                    }
                }
                metricsService.rebuildHourlyMetrics(168);
                metricsService.rebuildDailyMetrics(7);
                metricsService.clearDashboardCache();
                System.out.println("[Dashboard API] Real-time sync completed.");
            } catch (Exception e) {
                System.err.println("[Dashboard API] Real-time sync failed: " + e.getMessage());
            }
        }

        DashboardDataDto data = metricsService.getLiveDashboardData(shopId, date, refresh);
        long duration = System.currentTimeMillis() - start;
        System.out.println("[PERF-MEASURE] GET /api/dashboard/live response sent in " + duration + " ms. End time: " + System.currentTimeMillis() + " ms");
        return ResponseEntity.ok(data);
    }

    @org.springframework.web.bind.annotation.CrossOrigin
    @GetMapping("/api/dashboard/realtime-stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribeRealtimeStream() {
        return sseService.registerClient();
    }
}
