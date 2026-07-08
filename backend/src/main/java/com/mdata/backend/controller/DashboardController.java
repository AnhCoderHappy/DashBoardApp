package com.mdata.backend.controller;

import com.mdata.backend.dto.DashboardDataDto;
import com.mdata.backend.repository.PlatformConnectionRepository;
import com.mdata.backend.service.DashboardSnapshotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
public class DashboardController {

    private final PlatformConnectionRepository connectionRepository;
    private final DashboardSnapshotService dashboardSnapshotService;
    private final com.mdata.backend.service.SseService sseService;
    private final long startTime = System.currentTimeMillis();
    private static final DateTimeFormatter VN_OFFSET_FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    public DashboardController(
            PlatformConnectionRepository connectionRepository,
            DashboardSnapshotService dashboardSnapshotService,
            com.mdata.backend.service.SseService sseService
    ) {
        this.connectionRepository = connectionRepository;
        this.dashboardSnapshotService = dashboardSnapshotService;
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
        report.put("timestamp", VN_OFFSET_FORMATTER.format(Instant.now()));
        report.put("uptime", (System.currentTimeMillis() - startTime) / 1000.0);
        report.put("database", dbOk ? "ok" : "error");
        if (details != null) {
            report.put("details", details);
        }

        return ResponseEntity.status(dbOk ? 200 : 500).body(report);
    }

    @GetMapping("/api/dashboard/bootstrap")
    public ResponseEntity<DashboardSnapshotService.DashboardSnapshotResponse> getBootstrapDashboard(
            @org.springframework.web.bind.annotation.RequestParam UUID connectionId,
            @org.springframework.web.bind.annotation.RequestParam String date) {
        LocalDate targetDate = LocalDate.parse(date);
        return ResponseEntity.ok(dashboardSnapshotService.getBootstrapSnapshot(connectionId, targetDate));
    }

    @GetMapping("/api/dashboard/live")
    public ResponseEntity<DashboardDataDto> getLiveDashboard(
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "all") String shopId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String date,
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "false") boolean refresh) {
        long start = System.currentTimeMillis();
        System.out.println("[PERF-MEASURE] GET /api/dashboard/live request received for shopId: " + shopId + ", date: " + date + " at: " + start + " ms");

        LocalDate targetDate = date != null && !date.isBlank()
                ? LocalDate.parse(date)
                : LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        var data = dashboardSnapshotService.currentForShop(shopId, targetDate);
        if (refresh) {
            data = data.withRefreshQueued(true);
        }
        long duration = System.currentTimeMillis() - start;
        System.out.println("[PERF-MEASURE] GET /api/dashboard/live response sent in " + duration + " ms. End time: " + System.currentTimeMillis() + " ms");
        return ResponseEntity.ok(data.data());
    }

    @org.springframework.web.bind.annotation.CrossOrigin
    @GetMapping("/api/dashboard/realtime-stream")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribeRealtimeStream() {
        return sseService.registerClient();
    }
}
