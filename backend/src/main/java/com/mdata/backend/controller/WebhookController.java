package com.mdata.backend.controller;

import com.mdata.backend.connector.PancakeConnector;
import com.mdata.backend.entity.PlatformConnection;
import com.mdata.backend.repository.PlatformConnectionRepository;
import com.mdata.backend.service.MetricsService;
import com.mdata.backend.service.SseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final PlatformConnectionRepository connectionRepository;
    private final PancakeConnector pancakeConnector;
    private final MetricsService metricsService;
    private final SseService sseService;

    public WebhookController(
            PlatformConnectionRepository connectionRepository,
            PancakeConnector pancakeConnector,
            MetricsService metricsService,
            SseService sseService
    ) {
        this.connectionRepository = connectionRepository;
        this.pancakeConnector = pancakeConnector;
        this.metricsService = metricsService;
        this.sseService = sseService;
    }

    @PostMapping("/pancake/{shopId}")
    public ResponseEntity<?> handlePancakeWebhook(
            @PathVariable String shopId,
            @RequestBody String payloadBody
    ) {
        long start = System.currentTimeMillis();
        System.out.println("[PERF-MEASURE] Webhook received for shopId " + shopId + " at: " + start + " ms");
        try {
            PlatformConnection conn = connectionRepository.findByPlatform("pancake").stream()
                    .filter(c -> shopId.equals(c.getShopId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Connection not found for shopId: " + shopId));

            // Process order payload
            pancakeConnector.processSingleOrderPayload(payloadBody, conn.getId());

            // Extract the date of the order from the payload to perform targeted rebuild
            LocalDate targetDate = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(payloadBody);
                com.fasterxml.jackson.databind.JsonNode dataNode = rootNode.has("data") ? rootNode.get("data") : rootNode;
                com.fasterxml.jackson.databind.JsonNode orderNode = dataNode.isArray() && dataNode.size() > 0 ? dataNode.get(0) : dataNode;

                if (orderNode.has("inserted_at") && !orderNode.get("inserted_at").isNull()) {
                    String insertedAtStr = orderNode.get("inserted_at").asText();
                    if (insertedAtStr.contains(".")) {
                        insertedAtStr = insertedAtStr.substring(0, insertedAtStr.indexOf("."));
                    }
                    java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(insertedAtStr);
                    targetDate = ldt.atZone(java.time.ZoneId.of("UTC")).withZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
                }
            } catch (Exception ex) {
                System.err.println("[Webhook] Error parsing order date from webhook payload, defaulting to today: " + ex.getMessage());
            }

            final LocalDate finalTargetDate = targetDate;
            System.out.println("[Webhook] Targeted date identified: " + finalTargetDate);

            // Rebuild hourly/daily metrics and clear cache (Asynchronously in background)
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                long asyncStart = System.currentTimeMillis();
                System.out.println("[PERF-MEASURE] [Async] Background metrics rebuild started for date: " + finalTargetDate + " at: " + asyncStart + " ms");
                try {
                    metricsService.rebuildHourlyMetricsForDate(finalTargetDate);
                    metricsService.rebuildDailyMetricsForDate(finalTargetDate);
                    metricsService.clearDashboardCache();

                    // Broadcast real-time order-update event to frontend
                    sseService.broadcast("order-update", "new-order");
                    long asyncDuration = System.currentTimeMillis() - asyncStart;
                    System.out.println("[PERF-MEASURE] [Async] Background metrics rebuild & SSE broadcast completed in " + asyncDuration + " ms. End time: " + System.currentTimeMillis() + " ms");
                } catch (Exception dbEx) {
                    System.err.println("[Webhook] [Async] Fatal DB error during metrics rebuild: " + dbEx.getMessage());
                    dbEx.printStackTrace();
                }
            });

            long duration = System.currentTimeMillis() - start;
            System.out.println("[PERF-MEASURE] Webhook processed (order saved, async metrics started) in " + duration + " ms. End time: " + System.currentTimeMillis() + " ms");
            return ResponseEntity.ok(Map.of("success", true, "message", "Webhook processed and async metrics rebuild started."));
        } catch (Exception e) {
            System.err.println("[Webhook] Error processing Pancake webhook: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
