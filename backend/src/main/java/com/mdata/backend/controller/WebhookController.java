package com.mdata.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdata.backend.entity.PlatformConnection;
import com.mdata.backend.entity.WebhookEvent;
import com.mdata.backend.repository.PlatformConnectionRepository;
import com.mdata.backend.repository.WebhookEventRepository;
import com.mdata.backend.service.DashboardProjectionService;
import com.mdata.backend.service.OrderIngestionService;
import com.mdata.backend.service.PancakeOrderNormalizer;
import com.mdata.backend.service.SseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final PlatformConnectionRepository connectionRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final OrderIngestionService orderIngestionService;
    private final DashboardProjectionService dashboardProjectionService;
    private final PancakeOrderNormalizer pancakeOrderNormalizer;
    private final SseService sseService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter VN_OFFSET_FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    public WebhookController(
            PlatformConnectionRepository connectionRepository,
            WebhookEventRepository webhookEventRepository,
            OrderIngestionService orderIngestionService,
            DashboardProjectionService dashboardProjectionService,
            PancakeOrderNormalizer pancakeOrderNormalizer,
            SseService sseService
    ) {
        this.connectionRepository = connectionRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.orderIngestionService = orderIngestionService;
        this.dashboardProjectionService = dashboardProjectionService;
        this.pancakeOrderNormalizer = pancakeOrderNormalizer;
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

            WebhookEvent event = new WebhookEvent();
            event.setPlatform("pancake");
            event.setConnectionId(conn.getId());
            event.setEventType("pancake_order");
            event.setEventId(java.util.UUID.randomUUID().toString());
            event.setPayloadHash(sha256(payloadBody));
            event.setPayload(payloadBody);
            event.setStatus("pending");
            event.setReceivedAt(Instant.now());
            WebhookEvent savedEvent = webhookEventRepository.save(event);

            Map<String, Object> receivedPayload = new LinkedHashMap<>();
            receivedPayload.put("connectionId", conn.getId());
            receivedPayload.put("shopId", shopId);
            receivedPayload.put("eventId", savedEvent.getId());
            receivedPayload.put("receivedAt", VN_OFFSET_FORMATTER.format(savedEvent.getReceivedAt()));
            receivedPayload.put("order", previewOrder(payloadBody, conn));
            sseService.broadcast("ORDER_RECEIVED", receivedPayload);

            java.util.concurrent.CompletableFuture.runAsync(() -> {
                WebhookEvent processingEvent = webhookEventRepository.findById(savedEvent.getId()).orElse(null);
                if (processingEvent == null) return;
                try {
                    processingEvent.setStatus("processing");
                    processingEvent.setLockedAt(Instant.now());
                    processingEvent.setLockedBy("webhook-async");
                    webhookEventRepository.save(processingEvent);

                    var result = orderIngestionService.processPancakePayload(conn.getId(), payloadBody, savedEvent.getId());
                    dashboardProjectionService.updateProjection(result);

                    processingEvent.setStatus("processed");
                    processingEvent.setProcessedAt(Instant.now());
                    processingEvent.setErrorMessage(null);
                    webhookEventRepository.save(processingEvent);

                    sseService.broadcast("ORDER_CONFIRMED", Map.of("connectionId", conn.getId(), "orders", result.getConfirmedOrderIds()));
                    sseService.broadcast("DASHBOARD_DELTA", Map.of("connectionId", conn.getId(), "affectedDates", result.getAffectedDates()));
                    sseService.broadcast("order-update", "new-order");
                } catch (Exception ex) {
                    processingEvent.setStatus("failed");
                    processingEvent.setAttemptCount((processingEvent.getAttemptCount() == null ? 0 : processingEvent.getAttemptCount()) + 1);
                    processingEvent.setNextRetryAt(Instant.now().plusSeconds(60));
                    processingEvent.setErrorMessage(ex.getMessage());
                    processingEvent.setProcessedAt(Instant.now());
                    webhookEventRepository.save(processingEvent);
                    sseService.broadcast("ORDER_PROCESS_FAILED", Map.of("connectionId", conn.getId(), "eventId", savedEvent.getId(), "error", ex.getMessage()));
                }
            });

            long duration = System.currentTimeMillis() - start;
            System.out.println("[PERF-MEASURE] Webhook accepted in " + duration + " ms. End time: " + System.currentTimeMillis() + " ms");
            return ResponseEntity.ok(Map.of("success", true, "eventId", savedEvent.getId(), "status", "accepted"));
        } catch (Exception e) {
            System.err.println("[Webhook] Error processing Pancake webhook: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    private String sha256(String payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, Object> previewOrder(String payloadBody, PlatformConnection conn) {
        try {
            JsonNode orderNode = firstOrderNode(objectMapper.readTree(payloadBody));
            if (orderNode == null || orderNode.isMissingNode() || orderNode.isNull()) {
                return null;
            }
            var order = pancakeOrderNormalizer.normalize(orderNode, conn.getId());
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("id", "pending-" + order.platformOrderId());
            preview.put("createdAt", VN_OFFSET_FORMATTER.format(order.businessTime()));
            preview.put("orderCode", order.platformOrderId());
            preview.put("customerDisplayName", order.customerName());
            preview.put("platform", order.sourceChannel());
            preview.put("orderValue", order.netRevenue() != null ? order.netRevenue() : BigDecimal.ZERO);
            return preview;
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode firstOrderNode(JsonNode root) {
        JsonNode data = root.has("data") ? root.get("data") : root;
        if (data.isArray()) {
            return data.isEmpty() ? null : data.get(0);
        }
        return data;
    }
}
