package com.mdata.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdata.backend.entity.Order;
import com.mdata.backend.entity.OrderItem;
import com.mdata.backend.entity.ProductDailyMetric;
import com.mdata.backend.entity.RealtimeOutbox;
import com.mdata.backend.repository.OrderItemRepository;
import com.mdata.backend.repository.OrderRepository;
import com.mdata.backend.repository.ProductDailyMetricRepository;
import com.mdata.backend.repository.RealtimeOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class DashboardProjectionService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MetricsService metricsService;
    private final DashboardSnapshotService snapshotService;
    private final DashboardCacheService cacheService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductDailyMetricRepository productDailyMetricRepository;
    private final RealtimeOutboxRepository realtimeOutboxRepository;

    public DashboardProjectionService(
            MetricsService metricsService,
            DashboardSnapshotService snapshotService,
            DashboardCacheService cacheService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ProductDailyMetricRepository productDailyMetricRepository,
            RealtimeOutboxRepository realtimeOutboxRepository
    ) {
        this.metricsService = metricsService;
        this.snapshotService = snapshotService;
        this.cacheService = cacheService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.productDailyMetricRepository = productDailyMetricRepository;
        this.realtimeOutboxRepository = realtimeOutboxRepository;
    }

    @Transactional
    public void updateProjection(OrderIngestionResult result) {
        for (LocalDate date : result.getAffectedDates()) {
            metricsService.rebuildHourlyMetricsForDate(date);
            metricsService.rebuildDailyMetricsForDate(date);
            rebuildProductDailyMetrics(result.getConnectionId(), date);
            cacheService.evictDate(result.getConnectionId(), date);
            DashboardSnapshotService.DashboardSnapshotResponse snapshot = snapshotService.rebuildBootstrapSnapshot(result.getConnectionId(), date);
            createOutbox(result.getConnectionId(), "DASHBOARD_DELTA", "dashboard", null, snapshot.meta());
            createOutbox(result.getConnectionId(), "DASHBOARD_VERSION_UPDATED", "dashboard", null, snapshot.meta());
        }
    }

    private void rebuildProductDailyMetrics(UUID connectionId, LocalDate date) {
        productDailyMetricRepository.deleteByConnectionIdAndMetricDate(connectionId, date);
        Map<String, ProductAgg> aggMap = new HashMap<>();
        for (Order order : orderRepository.findByConnectionIdAndBusinessDate(connectionId, date)) {
            List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
            for (OrderItem item : items) {
                String key = String.join("|",
                        safe(order.getSourceChannel()),
                        safe(item.getPlatformProductId()),
                        safe(item.getPlatformSkuId()),
                        safe(item.getSku()),
                        safe(item.getProductName()));
                ProductAgg agg = aggMap.computeIfAbsent(key, ignored -> new ProductAgg(order, item));
                if ("refunded".equals(order.getNormalizedStatus())) {
                    agg.refundCount++;
                    continue;
                }
                if ("cancelled".equals(order.getNormalizedStatus())) {
                    continue;
                }
                agg.quantity += item.getQuantity() != null ? item.getQuantity() : 0;
                agg.grossRevenue = agg.grossRevenue.add(item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO);
                agg.netRevenue = agg.netRevenue.add(item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO);
                agg.orderIds.add(order.getId());
            }
        }
        for (ProductAgg agg : aggMap.values()) {
            ProductDailyMetric metric = new ProductDailyMetric();
            metric.setConnectionId(connectionId);
            metric.setMetricDate(date);
            metric.setSourceChannel(agg.sourceChannel);
            metric.setPlatformProductId(agg.platformProductId);
            metric.setPlatformSkuId(agg.platformSkuId);
            metric.setSku(agg.sku);
            metric.setProductName(agg.productName);
            metric.setQuantitySold(agg.quantity);
            metric.setGrossRevenue(agg.grossRevenue);
            metric.setNetRevenue(agg.netRevenue);
            metric.setOrderCount(agg.orderIds.size());
            metric.setRefundCount(agg.refundCount);
            metric.setUpdatedAt(Instant.now());
            productDailyMetricRepository.save(metric);
        }
    }

    private void createOutbox(UUID connectionId, String eventType, String aggregateType, UUID aggregateId, Object payload) {
        try {
            RealtimeOutbox outbox = new RealtimeOutbox();
            outbox.setConnectionId(connectionId);
            outbox.setEventType(eventType);
            outbox.setAggregateType(aggregateType);
            outbox.setAggregateId(aggregateId);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            realtimeOutboxRepository.save(outbox);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create realtime outbox event", e);
        }
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private static class ProductAgg {
        final String sourceChannel;
        final String platformProductId;
        final String platformSkuId;
        final String sku;
        final String productName;
        int quantity;
        BigDecimal grossRevenue = BigDecimal.ZERO;
        BigDecimal netRevenue = BigDecimal.ZERO;
        int refundCount;
        Set<UUID> orderIds = new HashSet<>();

        ProductAgg(Order order, OrderItem item) {
            this.sourceChannel = order.getSourceChannel() != null ? order.getSourceChannel() : "unknown";
            this.platformProductId = item.getPlatformProductId();
            this.platformSkuId = item.getPlatformSkuId();
            this.sku = item.getSku();
            this.productName = item.getProductName();
        }
    }
}
