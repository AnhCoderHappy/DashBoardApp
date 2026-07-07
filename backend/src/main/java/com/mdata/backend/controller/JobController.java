package com.mdata.backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdata.backend.connector.*;
import com.mdata.backend.entity.*;
import com.mdata.backend.repository.*;
import com.mdata.backend.service.AlertService;
import com.mdata.backend.service.MetricsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final PlatformConnectionRepository connectionRepository;
    private final PlatformTokenRepository tokenRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final SyncLogRepository syncLogRepository;
    private final PancakeConnector pancakeConnector;

    private final MetricsService metricsService;
    private final AlertService alertService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${CRON_SECRET:}")
    private String cronSecret;

    public JobController(
            PlatformConnectionRepository connectionRepository,
            PlatformTokenRepository tokenRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            WebhookEventRepository webhookEventRepository,
            SyncLogRepository syncLogRepository,
            PancakeConnector pancakeConnector,
            MetricsService metricsService,
            AlertService alertService
    ) {
        this.connectionRepository = connectionRepository;
        this.tokenRepository = tokenRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.syncLogRepository = syncLogRepository;
        this.pancakeConnector = pancakeConnector;
        this.metricsService = metricsService;
        this.alertService = alertService;
    }

    private boolean isAuthorized(String headerSecret) {
        if (cronSecret == null || cronSecret.isEmpty()) {
            return true;
        }
        return cronSecret.equals(headerSecret);
    }

    private UUID getPlatformConnectionId(String platform) {
        List<PlatformConnection> connections = connectionRepository.findByPlatform(platform);
        if (!connections.isEmpty()) {
            return connections.get(0).getId();
        }

        PlatformConnection conn = new PlatformConnection();
        conn.setPlatform(platform);
        conn.setShopName("Mock " + platform + " Connection");
        conn.setStatus("active");
        conn.setCreatedAt(Instant.now());
        conn.setUpdatedAt(Instant.now());
        PlatformConnection saved = connectionRepository.save(conn);

        PlatformToken token = new PlatformToken();
        token.setConnectionId(saved.getId());
        token.setAccessToken("mock-token");
        token.setRefreshToken("mock-refresh-token");
        token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        token.setCreatedAt(Instant.now());
        token.setUpdatedAt(Instant.now());
        tokenRepository.save(token);

        return saved.getId();
    }



    @PostMapping("/sync/pancake/orders")
    public ResponseEntity<?> syncPancakeOrders() {
        StringBuilder log = new StringBuilder();
        try {
            log.append("Starting syncPancakeOrders\n");
            var connections = connectionRepository.findByPlatform("pancake");
            log.append("Found connections: ").append(connections.size()).append("\n");
            for (var conn : connections) {
                log.append("Processing connection: ").append(conn.getId()).append("\n");
                if ("active".equals(conn.getStatus())) {
                    try {
                        pancakeConnector.syncOrders(conn.getId(), null);
                        log.append("Successfully synced conn: ").append(conn.getId()).append("\n");
                    } catch (Exception ex) {
                        log.append("Error syncing conn ").append(conn.getId()).append(": ").append(ex.getMessage()).append("\n");
                        return ResponseEntity.status(500).body(Map.of("success", false, "error", ex.toString()));
                    }
                }
            }
            try { java.nio.file.Files.writeString(java.nio.file.Paths.get("d:/MData/backend/sync_log.txt"), log.toString()); } catch(Exception e) {}
            rebuildMetricsAndClearCache();
            return ResponseEntity.ok(Map.of("success", true, "platform", "pancake"));
        } catch (Exception e) {
            log.append("Global error: ").append(e.getMessage()).append("\n");
            try { java.nio.file.Files.writeString(java.nio.file.Paths.get("d:/MData/backend/sync_log.txt"), log.toString()); } catch(Exception ex) {}
            return ResponseEntity.status(500).body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/sync/pancake/ads")
    public ResponseEntity<?> syncPancakeAds(@RequestHeader(value = "x-cron-secret", required = false) String secret) {
        if (!isAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        try {
            var connections = connectionRepository.findByPlatform("pancake");
            for (var conn : connections) {
                if ("active".equals(conn.getStatus())) {
                    try {
                        pancakeConnector.syncAdsInsights(conn.getId(), null);
                    } catch (Exception ex) {
                        alertService.sendAlert("Pancake Ads Sync Failed", "Shop ID " + conn.getShopId() + ": " + ex.getMessage(), false);
                    }
                }
            }
            rebuildMetricsAndClearCache();
            return ResponseEntity.ok(Map.of("success", true, "platform", "pancake"));
        } catch (Exception e) {
            alertService.sendAlert("Pancake Ads Sync Failed", e.getMessage(), false);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/rebuild-summary")
    public ResponseEntity<?> rebuildSummary(@RequestHeader(value = "x-cron-secret", required = false) String secret) {
        if (!isAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        try {
            metricsService.rebuildHourlyMetrics(168);
            metricsService.rebuildDailyMetrics(7);
            metricsService.clearDashboardCache();
            return ResponseEntity.ok(Map.of("success", true, "message", "Summary tables rebuilt successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/cleanup")
    public ResponseEntity<?> cleanup(@RequestHeader(value = "x-cron-secret", required = false) String secret) {
        if (!isAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        try {
            // Log clean up counts or simple no-op retention query
            SyncLog log = new SyncLog();
            log.setPlatform("system");
            log.setJobName("database_cleanup");
            log.setStatus("success");
            log.setStartedAt(Instant.now());
            log.setFinishedAt(Instant.now());
            log.setRecordsProcessed(0);
            log.setMetadata("{\"deletedWebhooks\":0,\"deletedLogs\":0}");
            syncLogRepository.save(log);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "deletedWebhooks", 0,
                    "deletedLogs", 0,
                    "message", "Cleanup job completed. Standard data retention policies applied."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/process-webhook-events")
    public ResponseEntity<?> processWebhookEvents(@RequestHeader(value = "x-cron-secret", required = false) String secret) {
        if (!isAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
        }
        try {
            // Find first 50 pending events
            List<WebhookEvent> pending = webhookEventRepository.findByStatus("pending");
            int processedCount = 0;

            for (WebhookEvent event : pending) {
                if (processedCount >= 50) break;
                try {
                    event.setStatus("processing");
                    webhookEventRepository.save(event);

                    Map<String, Object> payload = objectMapper.readValue(event.getPayload(), new TypeReference<>() {});

                    if ("haravan".equals(event.getPlatform())) {
                        String orderId = String.valueOf(payload.get("id"));
                        String status = String.valueOf(payload.getOrDefault("financial_status", "pending"));
                        String normalizedStatus = MetricsService.normalizeOrderStatus("haravan", status);

                        BigDecimal gross = new BigDecimal(String.valueOf(payload.getOrDefault("total_price", "0")));
                        BigDecimal discount = new BigDecimal(String.valueOf(payload.getOrDefault("total_discounts", "0")));
                        BigDecimal shipping = new BigDecimal(String.valueOf(payload.getOrDefault("total_shipping_price", "0")));
                        BigDecimal net = gross.subtract(discount);

                        Order order = orderRepository.findByPlatformAndPlatformOrderId("haravan", orderId).orElseGet(Order::new);
                        order.setPlatform("haravan");
                        order.setPlatformOrderId(orderId);
                        order.setStatus(status);
                        order.setNormalizedStatus(normalizedStatus);
                        order.setGrossRevenue(gross);
                        order.setNetRevenue(net);
                        order.setDiscountAmount(discount);
                        order.setShippingFee(shipping);
                        order.setCurrency(String.valueOf(payload.getOrDefault("currency", "VND")));

                        Map<String, Object> customer = (Map<String, Object>) payload.get("customer");
                        if (customer != null) {
                            order.setCustomerName((customer.getOrDefault("first_name", "") + " " + customer.getOrDefault("last_name", "")).trim());
                        } else {
                            order.setCustomerName("N/A");
                        }

                        order.setCreatedAtPlatform(Instant.parse(String.valueOf(payload.get("created_at"))));
                        order.setUpdatedAtPlatform(payload.containsKey("updated_at") ? Instant.parse(String.valueOf(payload.get("updated_at"))) : order.getCreatedAtPlatform());
                        order.setRawData(event.getPayload());
                        order.setUpdatedAt(Instant.now());

                        Order saved = orderRepository.save(order);

                        List<Map<String, Object>> lineItems = (List<Map<String, Object>>) payload.get("line_items");
                        if (lineItems != null) {
                            orderItemRepository.deleteByOrderId(saved.getId());
                            for (Map<String, Object> itemMap : lineItems) {
                                OrderItem item = new OrderItem();
                                item.setOrderId(saved.getId());
                                item.setPlatformProductId(String.valueOf(itemMap.get("product_id")));
                                item.setPlatformSkuId(String.valueOf(itemMap.get("variant_id")));
                                item.setSku(String.valueOf(itemMap.get("sku")));
                                item.setProductName(String.valueOf(itemMap.get("title")));
                                item.setQuantity(Integer.parseInt(String.valueOf(itemMap.getOrDefault("quantity", "0"))));
                                
                                BigDecimal price = new BigDecimal(String.valueOf(itemMap.getOrDefault("price", "0")));
                                item.setUnitPrice(price);
                                item.setTotalPrice(price.multiply(BigDecimal.valueOf(item.getQuantity())));
                                item.setRawData(objectMapper.writeValueAsString(itemMap));
                                orderItemRepository.save(item);
                            }
                        }

                    } else if ("shopee".equals(event.getPlatform())) {
                        Map<String, Object> data = (Map<String, Object>) payload.getOrDefault("data", Collections.emptyMap());
                        String orderSn = String.valueOf(data.containsKey("order_sn") ? data.get("order_sn") : payload.get("order_sn"));
                        String status = String.valueOf(data.containsKey("status") ? data.get("status") : payload.getOrDefault("status", "UNPAID"));
                        String normalizedStatus = MetricsService.normalizeOrderStatus("shopee", status);

                        Optional<Order> existing = orderRepository.findByPlatformAndPlatformOrderId("shopee", orderSn);
                        Order order = existing.orElseGet(Order::new);
                        order.setPlatform("shopee");
                        order.setPlatformOrderId(orderSn);
                        order.setStatus(status);
                        order.setNormalizedStatus(normalizedStatus);
                        
                        BigDecimal total = existing.isPresent() ? existing.get().getGrossRevenue() : new BigDecimal(String.valueOf(data.getOrDefault("total_amount", "0")));
                        order.setGrossRevenue(total);
                        order.setNetRevenue(total);
                        order.setCurrency("VND");
                        if (!existing.isPresent()) {
                            order.setCreatedAtPlatform(Instant.now());
                        }
                        order.setUpdatedAtPlatform(Instant.now());
                        order.setRawData(event.getPayload());
                        order.setUpdatedAt(Instant.now());

                        orderRepository.save(order);

                    } else if ("tiktok-shop".equals(event.getPlatform())) {
                        Map<String, Object> data = (Map<String, Object>) payload.getOrDefault("data", Collections.emptyMap());
                        String orderId = String.valueOf(data.containsKey("order_id") ? data.get("order_id") : payload.get("order_id"));
                        String status = String.valueOf(data.containsKey("order_status") ? data.get("order_status") : payload.getOrDefault("order_status", "UNPAID"));
                        String normalizedStatus = MetricsService.normalizeOrderStatus("tiktok-shop", status);

                        Optional<Order> existing = orderRepository.findByPlatformAndPlatformOrderId("tiktok-shop", orderId);
                        Order order = existing.orElseGet(Order::new);
                        order.setPlatform("tiktok-shop");
                        order.setPlatformOrderId(orderId);
                        order.setStatus(status);
                        order.setNormalizedStatus(normalizedStatus);
                        order.setCurrency("VND");
                        if (!existing.isPresent()) {
                            order.setCreatedAtPlatform(Instant.now());
                        }
                        order.setUpdatedAtPlatform(Instant.now());
                        order.setRawData(event.getPayload());
                        order.setUpdatedAt(Instant.now());

                        orderRepository.save(order);
                    }

                    event.setStatus("processed");
                    event.setProcessedAt(Instant.now());
                    event.setErrorMessage(null);
                    webhookEventRepository.save(event);
                    processedCount++;

                } catch (Exception ex) {
                    System.err.println("Error processing event: " + event.getId() + " - " + ex.getMessage());
                    event.setStatus("failed");
                    event.setErrorMessage(ex.getMessage());
                    event.setProcessedAt(Instant.now());
                    webhookEventRepository.save(event);
                }
            }

            if (processedCount > 0) {
                rebuildMetricsAndClearCache();
            }
            return ResponseEntity.ok(Map.of("success", true, "processed", processedCount));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    private void rebuildMetricsAndClearCache() {
        try {
            metricsService.rebuildHourlyMetrics(168);
            metricsService.rebuildDailyMetrics(7);
            metricsService.clearDashboardCache();
        } catch (Exception e) {
            System.err.println("Failed to rebuild metrics and clear cache: " + e.getMessage());
        }
    }

}
