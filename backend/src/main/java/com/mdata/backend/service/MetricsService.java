package com.mdata.backend.service;

import com.mdata.backend.dto.DashboardDataDto;
import com.mdata.backend.entity.*;
import com.mdata.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class MetricsService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService dashboardExecutor = Executors.newFixedThreadPool(10);

    private String getPancakeSourcePlatform(String rawData) {
        if (rawData == null || rawData.isEmpty()) {
            return "pos";
        }
        try {
            var node = objectMapper.readTree(rawData);
            if (node.has("order_sources_name") && !node.get("order_sources_name").isNull()) {
                String sourceName = node.get("order_sources_name").asText().trim().toLowerCase();
                if ("shopee".equals(sourceName)) return "shopee";
                if ("tiktok".equals(sourceName) || "tiktok shop".equals(sourceName) || "tiktok-shop".equals(sourceName)) return "tiktok-shop";
                if ("haravan".equals(sourceName)) return "haravan";
                if ("facebook".equals(sourceName)) return "facebook";
                if ("instagram".equals(sourceName)) return "instagram";
                if ("pancake_store".equals(sourceName) || "pancake-store".equals(sourceName) || "pancake store".equals(sourceName)) return "pos";
                if (!sourceName.isEmpty()) return sourceName;
            }
        } catch (Exception e) {
            // Ignore
        }
        return "pos";
    }

    private String orderSourceChannel(Order order) {
        if (order.getSourceChannel() != null && !order.getSourceChannel().isBlank()) {
            return order.getSourceChannel();
        }
        if ("pancake".equals(order.getPlatform())) {
            return getPancakeSourcePlatform(order.getRawData());
        }
        return order.getPlatform();
    }

    private Instant resolveBusinessTime(Order order) {
        if (order.getBusinessTime() != null) {
            return order.getBusinessTime();
        }
        if (order.getRawData() != null && !order.getRawData().isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(order.getRawData());
                String value = null;
                for (String field : List.of("inserted_at", "created_at", "updated_at")) {
                    JsonNode fieldValue = node.path(field);
                    if (!fieldValue.isMissingNode() && !fieldValue.isNull() && !fieldValue.asText().isBlank()) {
                        value = fieldValue.asText();
                        break;
                    }
                }
                if (value != null) {
                    if (value.matches("\\d+")) {
                        long epoch = Long.parseLong(value);
                        return epoch > 9_999_999_999L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
                    }
                    String clean = value.contains(".") && !value.endsWith("Z") ? value.substring(0, value.indexOf(".")) : value;
                    if (clean.endsWith("Z") || clean.contains("+")) {
                        return Instant.parse(clean);
                    }
                    return LocalDateTime.parse(clean).atZone(java.time.ZoneOffset.UTC).toInstant();
                }
            } catch (Exception ignored) {
                // ponytail: old raw Pancake variants fall back to stored platform time; add patterns when sample proves them.
            }
        }
        return order.getCreatedAtPlatform() != null ? order.getCreatedAtPlatform() : Instant.now();
    }

    private LocalDate resolveBusinessDate(Order order) {
        if (order.getBusinessDate() != null) {
            return order.getBusinessDate();
        }
        return resolveBusinessTime(order).atZone(VN_ZONE).toLocalDate();
    }

    private Instant businessHourBucket(Order order) {
        return resolveBusinessTime(order).atZone(VN_ZONE).truncatedTo(ChronoUnit.HOURS).toInstant();
    }

    private List<Order> findOrdersForBusinessDate(LocalDate targetDate, List<UUID> connectionIds, boolean hasShopFilter) {
        Instant start = targetDate.minusDays(1).atStartOfDay(VN_ZONE).toInstant();
        Instant end = targetDate.plusDays(2).atStartOfDay(VN_ZONE).toInstant();
        List<Order> orders;
        if (hasShopFilter && connectionIds != null && !connectionIds.isEmpty()) {
            orders = orderRepository.findByCreatedAtPlatformBetweenAndConnectionIdIn(start, end, connectionIds);
        } else if (hasShopFilter) {
            return List.of();
        } else {
            orders = orderRepository.findByCreatedAtPlatformBetween(start, end);
        }
        return orders.stream()
                .filter(order -> targetDate.equals(resolveBusinessDate(order)))
                .collect(Collectors.toList());
    }

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final AdInsightsHourlyRepository adInsightsHourlyRepository;
    private final HourlyMetricsRepository hourlyMetricsRepository;
    private final DailyMetricsRepository dailyMetricsRepository;
    private final PlatformHealthRepository platformHealthRepository;
    private final PlatformConnectionRepository connectionRepository;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(VN_ZONE);
    private final Map<String, DashboardDataDto> dashboardCache = new java.util.concurrent.ConcurrentHashMap<>();

    public void clearDashboardCache() {
        dashboardCache.clear();
    }

    public MetricsService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            AdInsightsHourlyRepository adInsightsHourlyRepository,
            HourlyMetricsRepository hourlyMetricsRepository,
            DailyMetricsRepository dailyMetricsRepository,
            PlatformHealthRepository platformHealthRepository,
            PlatformConnectionRepository connectionRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.adInsightsHourlyRepository = adInsightsHourlyRepository;
        this.hourlyMetricsRepository = hourlyMetricsRepository;
        this.dailyMetricsRepository = dailyMetricsRepository;
        this.platformHealthRepository = platformHealthRepository;
        this.connectionRepository = connectionRepository;
    }

    public static String normalizeOrderStatus(String platform, String platformStatus) {
        if (platformStatus == null) return "unknown";
        String status = platformStatus.toUpperCase().trim();
        switch (platform) {
            case "haravan":
                if (List.of("CANCELLED", "VOIDED").contains(status)) return "cancelled";
                if ("REFUNDED".equals(status)) return "refunded";
                if ("PAID".equals(status)) return "completed";
                if (List.of("PENDING", "AUTHORIZED").contains(status)) return "pending";
                return "processing";
            case "shopee":
                if ("UNPAID".equals(status)) return "pending";
                if (List.of("READY_TO_SHIP", "PROCESSED", "SHIPPED").contains(status)) return "processing";
                if ("COMPLETED".equals(status)) return "completed";
                if ("CANCELLED".equals(status)) return "cancelled";
                if (List.of("TO_RETURN", "RETURN").contains(status)) return "refunded";
                return "unknown";
            case "tiktok-shop":
                if ("UNPAID".equals(status)) return "pending";
                if (List.of("AWAITING_SHIPMENT", "AWAITING_COLLECTION", "PARTIALLY_SHIPPED", "SHIPPED", "DELIVERED").contains(status)) {
                    return "processing";
                }
                if ("COMPLETED".equals(status)) return "completed";
                if ("CANCELLED".equals(status)) return "cancelled";
                return "unknown";
            case "pancake":
                if (List.of("0", "1").contains(status)) return "pending";
                if (List.of("2", "8").contains(status)) return "processing";
                if ("3".equals(status)) return "completed";
                if ("6".equals(status)) return "cancelled";
                if (List.of("4", "5", "7").contains(status)) return "refunded";
                return "processing";
            default:
                return "unknown";
        }
    }

    @Transactional
    public void rebuildHourlyMetrics(int sinceHours) {
        Instant since = Instant.now().minus(sinceHours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);

        hourlyMetricsRepository.deleteByHourGreaterThanEqual(since);
        hourlyMetricsRepository.flush();

        List<Order> orders = orderRepository.findByCreatedAtPlatformGreaterThanEqual(since);
        List<AdInsightsHourly> adInsights = adInsightsHourlyRepository.findByHourGreaterThanEqual(since);

        Map<UUID, String> connectionToShopId = connectionRepository.findAll().stream()
                .collect(Collectors.toMap(PlatformConnection::getId, PlatformConnection::getShopId));

        // Grouping key: platform_shopId_hour
        Map<String, HourlyMetrics> aggMap = new HashMap<>();

        for (Order order : orders) {
            Instant hour = order.getCreatedAtPlatform().truncatedTo(ChronoUnit.HOURS);
            String shopId = order.getConnectionId() != null ? connectionToShopId.getOrDefault(order.getConnectionId(), "unknown") : "unknown";
            String platform = orderSourceChannel(order);
            String key = platform + "_" + shopId + "_" + hour.toString();

            final String finalPlatform = platform;
            HourlyMetrics metric = aggMap.computeIfAbsent(key, k -> {
                HourlyMetrics m = new HourlyMetrics();
                m.setPlatform(finalPlatform);
                m.setShopId(shopId);
                m.setHour(hour);
                return m;
            });

            if ("cancelled".equals(order.getNormalizedStatus())) {
                metric.setCancelledCount(metric.getCancelledCount() + 1);
            } else {
                metric.setOrderCount(metric.getOrderCount() + 1);
                if (List.of("completed", "processing", "pending").contains(order.getNormalizedStatus())) {
                    metric.setGrossRevenue(metric.getGrossRevenue().add(order.getGrossRevenue()));
                    metric.setNetRevenue(metric.getNetRevenue().add(order.getNetRevenue()));
                } else if ("refunded".equals(order.getNormalizedStatus())) {
                    metric.setRefundCount(metric.getRefundCount() + 1);
                } else {
                    metric.setGrossRevenue(metric.getGrossRevenue().add(order.getGrossRevenue()));
                }
            }
        }

        for (AdInsightsHourly ad : adInsights) {
            Instant hour = ad.getHour().truncatedTo(ChronoUnit.HOURS);
            String shopId = ad.getShopId() != null ? ad.getShopId() : "unknown";
            String key = ad.getPlatform() + "_" + shopId + "_" + hour.toString();

            HourlyMetrics metric = aggMap.computeIfAbsent(key, k -> {
                HourlyMetrics m = new HourlyMetrics();
                m.setPlatform(ad.getPlatform());
                m.setShopId(shopId);
                m.setHour(hour);
                return m;
            });

            metric.setAdSpend(metric.getAdSpend().add(ad.getSpend()));
        }

        for (HourlyMetrics metric : aggMap.values()) {
            if (metric.getAdSpend().compareTo(BigDecimal.ZERO) > 0) {
                metric.setRoas(metric.getNetRevenue().divide(metric.getAdSpend(), 4, RoundingMode.HALF_UP));
            } else {
                metric.setRoas(BigDecimal.ZERO);
            }
            metric.setUpdatedAt(Instant.now());
            hourlyMetricsRepository.save(metric);
        }
    }

    @Transactional
    public void rebuildDailyMetrics(int sinceDays) {
        LocalDate sinceDate = LocalDate.now(VN_ZONE).minusDays(sinceDays);

        dailyMetricsRepository.deleteByDateGreaterThanEqual(sinceDate);
        dailyMetricsRepository.flush();

        Instant since = sinceDate.atStartOfDay(VN_ZONE).toInstant();

        List<Order> orders = orderRepository.findByCreatedAtPlatformGreaterThanEqual(since);
        List<AdInsightsHourly> adInsights = adInsightsHourlyRepository.findByHourGreaterThanEqual(since);

        Map<UUID, String> connectionToShopId = connectionRepository.findAll().stream()
                .collect(Collectors.toMap(PlatformConnection::getId, PlatformConnection::getShopId));

        Map<String, DailyMetrics> aggMap = new HashMap<>();

        for (Order order : orders) {
            LocalDate localDate = order.getCreatedAtPlatform().atZone(VN_ZONE).toLocalDate();
            String shopId = order.getConnectionId() != null ? connectionToShopId.getOrDefault(order.getConnectionId(), "unknown") : "unknown";
            String platform = orderSourceChannel(order);
            String key = platform + "_" + shopId + "_" + localDate.toString();

            final String finalPlatform = platform;
            DailyMetrics metric = aggMap.computeIfAbsent(key, k -> {
                DailyMetrics m = new DailyMetrics();
                m.setPlatform(finalPlatform);
                m.setShopId(shopId);
                m.setDate(localDate);
                return m;
            });

            if ("cancelled".equals(order.getNormalizedStatus())) {
                metric.setCancelledCount(metric.getCancelledCount() + 1);
            } else {
                metric.setOrderCount(metric.getOrderCount() + 1);
                if (List.of("completed", "processing", "pending").contains(order.getNormalizedStatus())) {
                    metric.setGrossRevenue(metric.getGrossRevenue().add(order.getGrossRevenue()));
                    metric.setNetRevenue(metric.getNetRevenue().add(order.getNetRevenue()));
                } else if ("refunded".equals(order.getNormalizedStatus())) {
                    metric.setRefundCount(metric.getRefundCount() + 1);
                } else {
                    metric.setGrossRevenue(metric.getGrossRevenue().add(order.getGrossRevenue()));
                }
            }
        }

        for (AdInsightsHourly ad : adInsights) {
            LocalDate localDate = ad.getHour().atZone(VN_ZONE).toLocalDate();
            String shopId = ad.getShopId() != null ? ad.getShopId() : "unknown";
            String key = ad.getPlatform() + "_" + shopId + "_" + localDate.toString();

            DailyMetrics metric = aggMap.computeIfAbsent(key, k -> {
                DailyMetrics m = new DailyMetrics();
                m.setPlatform(ad.getPlatform());
                m.setShopId(shopId);
                m.setDate(localDate);
                return m;
            });

            metric.setAdSpend(metric.getAdSpend().add(ad.getSpend()));
        }

        for (DailyMetrics metric : aggMap.values()) {
            if (metric.getAdSpend().compareTo(BigDecimal.ZERO) > 0) {
                metric.setRoas(metric.getNetRevenue().divide(metric.getAdSpend(), 4, RoundingMode.HALF_UP));
            } else {
                metric.setRoas(BigDecimal.ZERO);
            }
            metric.setUpdatedAt(Instant.now());
            dailyMetricsRepository.save(metric);
        }
    }

    @Transactional
    public void rebuildDailyMetricsForDate(LocalDate targetDate) {
        dailyMetricsRepository.deleteByDate(targetDate);
        dailyMetricsRepository.flush();

        Instant startUTC = targetDate.atStartOfDay(VN_ZONE).toInstant();
        Instant endUTC = targetDate.plusDays(1).atStartOfDay(VN_ZONE).toInstant();

        List<Order> orders = findOrdersForBusinessDate(targetDate, null, false);
        List<AdInsightsHourly> adInsights = adInsightsHourlyRepository.findByHourBetween(startUTC, endUTC);

        Map<UUID, String> connectionToShopId = connectionRepository.findAll().stream()
                .collect(Collectors.toMap(PlatformConnection::getId, PlatformConnection::getShopId));

        Map<String, DailyMetrics> aggMap = new HashMap<>();

        for (Order order : orders) {
            LocalDate localDate = resolveBusinessDate(order);
            String shopId = order.getConnectionId() != null ? connectionToShopId.getOrDefault(order.getConnectionId(), "unknown") : "unknown";
            String platform = orderSourceChannel(order);
            String key = platform + "_" + shopId + "_" + localDate.toString();

            final String finalPlatform = platform;
            DailyMetrics metric = aggMap.computeIfAbsent(key, k -> {
                DailyMetrics m = new DailyMetrics();
                m.setPlatform(finalPlatform);
                m.setShopId(shopId);
                m.setDate(localDate);
                return m;
            });

            if ("cancelled".equals(order.getNormalizedStatus())) {
                metric.setCancelledCount(metric.getCancelledCount() + 1);
            } else {
                metric.setOrderCount(metric.getOrderCount() + 1);
                if (List.of("completed", "processing", "pending").contains(order.getNormalizedStatus())) {
                    metric.setGrossRevenue(metric.getGrossRevenue().add(order.getGrossRevenue()));
                    metric.setNetRevenue(metric.getNetRevenue().add(order.getNetRevenue()));
                } else if ("refunded".equals(order.getNormalizedStatus())) {
                    metric.setRefundCount(metric.getRefundCount() + 1);
                } else {
                    metric.setGrossRevenue(metric.getGrossRevenue().add(order.getGrossRevenue()));
                }
            }
        }

        for (AdInsightsHourly ad : adInsights) {
            LocalDate localDate = ad.getHour().atZone(VN_ZONE).toLocalDate();
            String shopId = ad.getShopId() != null ? ad.getShopId() : "unknown";
            String key = ad.getPlatform() + "_" + shopId + "_" + localDate.toString();

            DailyMetrics metric = aggMap.computeIfAbsent(key, k -> {
                DailyMetrics m = new DailyMetrics();
                m.setPlatform(ad.getPlatform());
                m.setShopId(shopId);
                m.setDate(localDate);
                return m;
            });

            metric.setAdSpend(metric.getAdSpend().add(ad.getSpend()));
        }

        for (DailyMetrics metric : aggMap.values()) {
            if (metric.getAdSpend().compareTo(BigDecimal.ZERO) > 0) {
                metric.setRoas(metric.getNetRevenue().divide(metric.getAdSpend(), 4, RoundingMode.HALF_UP));
            } else {
                metric.setRoas(BigDecimal.ZERO);
            }
            metric.setUpdatedAt(Instant.now());
            dailyMetricsRepository.save(metric);
        }
    }

    @Transactional
    public void rebuildHourlyMetricsForDate(LocalDate targetDate) {
        Instant start = targetDate.atStartOfDay(VN_ZONE).toInstant();
        Instant end = targetDate.plusDays(1).atStartOfDay(VN_ZONE).toInstant();

        hourlyMetricsRepository.deleteByHourBetween(start, end);
        hourlyMetricsRepository.flush();

        List<Order> orders = findOrdersForBusinessDate(targetDate, null, false);
        List<AdInsightsHourly> adInsights = adInsightsHourlyRepository.findByHourBetween(start, end);

        Map<UUID, String> connectionToShopId = connectionRepository.findAll().stream()
                .collect(Collectors.toMap(PlatformConnection::getId, PlatformConnection::getShopId));

        Map<String, HourlyMetrics> aggMap = new HashMap<>();

        for (Order order : orders) {
            Instant hour = businessHourBucket(order);
            String shopId = order.getConnectionId() != null ? connectionToShopId.getOrDefault(order.getConnectionId(), "unknown") : "unknown";
            String platform = orderSourceChannel(order);
            String key = platform + "_" + shopId + "_" + hour.toString();

            final String finalPlatform = platform;
            HourlyMetrics metric = aggMap.computeIfAbsent(key, k -> {
                HourlyMetrics m = new HourlyMetrics();
                m.setPlatform(finalPlatform);
                m.setShopId(shopId);
                m.setHour(hour);
                return m;
            });

            if ("cancelled".equals(order.getNormalizedStatus())) {
                metric.setCancelledCount(metric.getCancelledCount() + 1);
            } else {
                metric.setOrderCount(metric.getOrderCount() + 1);
                if (List.of("completed", "processing", "pending").contains(order.getNormalizedStatus())) {
                    metric.setGrossRevenue(metric.getGrossRevenue().add(order.getGrossRevenue()));
                    metric.setNetRevenue(metric.getNetRevenue().add(order.getNetRevenue()));
                } else if ("refunded".equals(order.getNormalizedStatus())) {
                    metric.setRefundCount(metric.getRefundCount() + 1);
                } else {
                    metric.setGrossRevenue(metric.getGrossRevenue().add(order.getGrossRevenue()));
                }
            }
        }

        for (AdInsightsHourly ad : adInsights) {
            Instant hour = ad.getHour().truncatedTo(ChronoUnit.HOURS);
            String shopId = ad.getShopId() != null ? ad.getShopId() : "unknown";
            String key = ad.getPlatform() + "_" + shopId + "_" + hour.toString();

            HourlyMetrics metric = aggMap.computeIfAbsent(key, k -> {
                HourlyMetrics m = new HourlyMetrics();
                m.setPlatform(ad.getPlatform());
                m.setShopId(shopId);
                m.setHour(hour);
                return m;
            });

            metric.setAdSpend(metric.getAdSpend().add(ad.getSpend()));
        }

        for (HourlyMetrics metric : aggMap.values()) {
            if (metric.getAdSpend().compareTo(BigDecimal.ZERO) > 0) {
                metric.setRoas(metric.getNetRevenue().divide(metric.getAdSpend(), 4, RoundingMode.HALF_UP));
            } else {
                metric.setRoas(BigDecimal.ZERO);
            }
            metric.setUpdatedAt(Instant.now());
            hourlyMetricsRepository.save(metric);
        }
    }

    public DashboardDataDto getLiveDashboardData(String filterShopId, String dateStr, boolean forceRefresh) {
        LocalDate targetDate;
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                targetDate = LocalDate.parse(dateStr);
            } catch (Exception e) {
                targetDate = LocalDate.now(VN_ZONE);
            }
        } else {
            targetDate = LocalDate.now(VN_ZONE);
        }
        LocalDate VN_TODAY = LocalDate.now(VN_ZONE);
        boolean isPastDate = targetDate.isBefore(VN_TODAY);
        String cacheKey = (filterShopId != null ? filterShopId : "all") + "_" + targetDate.toString();
        if (!forceRefresh && isPastDate && dashboardCache.containsKey(cacheKey)) {
            System.out.println("[Cache] Serving dashboard data for " + cacheKey + " from in-memory cache!");
            return dashboardCache.get(cacheKey);
        }

        Instant localMidnightUTC = targetDate.atStartOfDay(VN_ZONE).toInstant();
        Instant endOfDayUTC = targetDate.plusDays(1).atStartOfDay(VN_ZONE).toInstant();

        List<UUID> connectionIds = null;
        boolean hasShopFilter = filterShopId != null && !filterShopId.isEmpty() && !"all".equals(filterShopId);
        if (hasShopFilter) {
            connectionIds = connectionRepository.findAll().stream()
                    .filter(c -> filterShopId.equals(c.getShopId()))
                    .map(PlatformConnection::getId)
                    .collect(Collectors.toList());
        }

        DashboardDataDto dto = new DashboardDataDto();

        // Define final variables for lambda expressions
        final LocalDate fTargetDate = targetDate;
        final String fFilterShopId = filterShopId;
        final LocalDate fYesterdayDate = targetDate.minusDays(1);
        final Instant fYesterdayMidnightUTC = fYesterdayDate.atStartOfDay(VN_ZONE).toInstant();

        // Define futures for parallel execution
        CompletableFuture<List<PlatformHealth>> healthFuture = CompletableFuture.supplyAsync(
            () -> {
                long s = System.currentTimeMillis();
                List<PlatformHealth> res = platformHealthRepository.findAll();
                System.out.println("[PERF-TELEMETRY] Query health took: " + (System.currentTimeMillis() - s) + " ms");
                return res;
            }, dashboardExecutor
        );

        final Instant fLocalMidnightUTC = localMidnightUTC;
        final Instant fEndOfDayUTC = endOfDayUTC;
        final List<UUID> fConnectionIds = connectionIds;
        final boolean fHasShopFilter = hasShopFilter;

        CompletableFuture<List<DailyMetrics>> dailyMetricsFuture = CompletableFuture.supplyAsync(
            () -> {
                long s = System.currentTimeMillis();
                List<DailyMetrics> res;
                if (fFilterShopId != null && !fFilterShopId.isEmpty() && !"all".equals(fFilterShopId)) {
                    List<DailyMetrics> list = new ArrayList<>(dailyMetricsRepository.findByShopIdAndDate(fFilterShopId, fTargetDate));
                    list.addAll(dailyMetricsRepository.findByShopIdAndDate("unknown", fTargetDate));
                    res = list;
                } else {
                    res = dailyMetricsRepository.findByDate(fTargetDate);
                }
                System.out.println("[PERF-TELEMETRY] Query dailyMetrics took: " + (System.currentTimeMillis() - s) + " ms");
                return res;
            }, dashboardExecutor
        );

        CompletableFuture<List<DailyMetrics>> yesterdayMetricsFuture = CompletableFuture.supplyAsync(
            () -> {
                long s = System.currentTimeMillis();
                List<DailyMetrics> res;
                if (fFilterShopId != null && !fFilterShopId.isEmpty() && !"all".equals(fFilterShopId)) {
                    List<DailyMetrics> list = new ArrayList<>(dailyMetricsRepository.findByShopIdAndDate(fFilterShopId, fYesterdayDate));
                    list.addAll(dailyMetricsRepository.findByShopIdAndDate("unknown", fYesterdayDate));
                    res = list;
                } else {
                    res = dailyMetricsRepository.findByDate(fYesterdayDate);
                }
                System.out.println("[PERF-TELEMETRY] Query yesterdayMetrics took: " + (System.currentTimeMillis() - s) + " ms");
                return res;
            }, dashboardExecutor
        );

        CompletableFuture<List<Order>> dateOrdersFuture = CompletableFuture.supplyAsync(
            () -> {
                long s = System.currentTimeMillis();
                List<Order> res = findOrdersForBusinessDate(fTargetDate, fConnectionIds, fHasShopFilter);
                System.out.println("[PERF-TELEMETRY] Query dateOrders took: " + (System.currentTimeMillis() - s) + " ms");
                return res;
            }, dashboardExecutor
        );

        CompletableFuture<List<HourlyMetrics>> hourlyMetricsFuture = CompletableFuture.supplyAsync(
            () -> {
                long s = System.currentTimeMillis();
                List<HourlyMetrics> res;
                if (fHasShopFilter && fConnectionIds != null && !fConnectionIds.isEmpty()) {
                    List<HourlyMetrics> list = new ArrayList<>(hourlyMetricsRepository.findByShopIdAndHourBetweenOrderByHourAsc(fFilterShopId, fLocalMidnightUTC, fEndOfDayUTC));
                    list.addAll(hourlyMetricsRepository.findByShopIdAndHourBetweenOrderByHourAsc("unknown", fLocalMidnightUTC, fEndOfDayUTC));
                    res = list;
                } else {
                    res = hourlyMetricsRepository.findByHourBetweenOrderByHourAsc(fLocalMidnightUTC, fEndOfDayUTC);
                }
                System.out.println("[PERF-TELEMETRY] Query hourlyMetrics took: " + (System.currentTimeMillis() - s) + " ms");
                return res;
            }, dashboardExecutor
        );

        CompletableFuture<List<HourlyMetrics>> yesterdayHourlyMetricsFuture = CompletableFuture.supplyAsync(
            () -> {
                long s = System.currentTimeMillis();
                List<HourlyMetrics> res;
                if (fHasShopFilter && fConnectionIds != null && !fConnectionIds.isEmpty()) {
                    List<HourlyMetrics> list = new ArrayList<>(hourlyMetricsRepository.findByShopIdAndHourBetweenOrderByHourAsc(fFilterShopId, fYesterdayMidnightUTC, fLocalMidnightUTC));
                    list.addAll(hourlyMetricsRepository.findByShopIdAndHourBetweenOrderByHourAsc("unknown", fYesterdayMidnightUTC, fLocalMidnightUTC));
                    res = list;
                } else {
                    res = hourlyMetricsRepository.findByHourBetweenOrderByHourAsc(fYesterdayMidnightUTC, fLocalMidnightUTC);
                }
                System.out.println("[PERF-TELEMETRY] Query yesterdayHourlyMetrics took: " + (System.currentTimeMillis() - s) + " ms");
                return res;
            }, dashboardExecutor
        );

        CompletableFuture<List<Order>> latestOrdersFuture = CompletableFuture.supplyAsync(
            () -> {
                long s = System.currentTimeMillis();
                List<Order> res = findOrdersForBusinessDate(fTargetDate, fConnectionIds, fHasShopFilter);
                System.out.println("[PERF-TELEMETRY] Query latestOrders took: " + (System.currentTimeMillis() - s) + " ms");
                return res;
            }, dashboardExecutor
        );

        CompletableFuture<List<Object[]>> rawProductsFuture = CompletableFuture.supplyAsync(
            () -> {
                long s = System.currentTimeMillis();
                List<Object[]> res;
                if (fHasShopFilter && fConnectionIds != null && !fConnectionIds.isEmpty()) {
                    res = orderItemRepository.findTopProductsBetweenAndConnectionIdIn(fLocalMidnightUTC, fEndOfDayUTC, fConnectionIds);
                } else if (fHasShopFilter) {
                    res = List.of();
                } else {
                    res = orderItemRepository.findTopProductsBetween(fLocalMidnightUTC, fEndOfDayUTC);
                }
                System.out.println("[PERF-TELEMETRY] Query rawProducts took: " + (System.currentTimeMillis() - s) + " ms");
                return res;
            }, dashboardExecutor
        );

        CompletableFuture<List<Object[]>> rawItemsFuture = CompletableFuture.supplyAsync(
            () -> {
                long s = System.currentTimeMillis();
                List<Object[]> res;
                if (fHasShopFilter && fConnectionIds != null && !fConnectionIds.isEmpty()) {
                    res = orderItemRepository.findItemsWithPlatformBetweenAndConnectionIdIn(fLocalMidnightUTC, fEndOfDayUTC, fConnectionIds);
                } else if (fHasShopFilter) {
                    res = List.of();
                } else {
                    res = orderItemRepository.findItemsWithPlatformBetween(fLocalMidnightUTC, fEndOfDayUTC);
                }
                System.out.println("[PERF-TELEMETRY] Query rawItems took: " + (System.currentTimeMillis() - s) + " ms");
                return res;
            }, dashboardExecutor
        );

        CompletableFuture<List<AdInsightsHourly>> insightsFuture = CompletableFuture.supplyAsync(
            () -> {
                long s = System.currentTimeMillis();
                List<AdInsightsHourly> res;
                if (fHasShopFilter && fFilterShopId != null && !fFilterShopId.isEmpty()) {
                    res = adInsightsHourlyRepository.findByShopIdAndHourBetween(fFilterShopId, fLocalMidnightUTC, fEndOfDayUTC);
                } else {
                    res = adInsightsHourlyRepository.findByHourBetween(fLocalMidnightUTC, fEndOfDayUTC);
                }
                System.out.println("[PERF-TELEMETRY] Query insights took: " + (System.currentTimeMillis() - s) + " ms");
                return res;
            }, dashboardExecutor
        );

        // Block and wait for all to complete in parallel
        long parallelQueriesStart = System.currentTimeMillis();
        CompletableFuture.allOf(
            healthFuture,
            dailyMetricsFuture,
            yesterdayMetricsFuture,
            dateOrdersFuture,
            hourlyMetricsFuture,
            yesterdayHourlyMetricsFuture,
            latestOrdersFuture,
            rawProductsFuture,
            rawItemsFuture,
            insightsFuture
        ).join();
        long parallelQueriesDuration = System.currentTimeMillis() - parallelQueriesStart;
        System.out.println("[PERF-MEASURE] [Parallel Queries] All 10 queries executed concurrently in " + parallelQueriesDuration + " ms");

        // 1. Platform Health
        Map<String, DashboardDataDto.PlatformHealthDetailsDto> healthMap = new HashMap<>();
        List<PlatformHealth> healthList = healthFuture.join();
        for (PlatformHealth h : healthList) {
            DashboardDataDto.PlatformHealthDetailsDto details = new DashboardDataDto.PlatformHealthDetailsDto();
            details.setStatus(h.getStatus());
            details.setLabel("LIVE".equalsIgnoreCase(h.getStatus()) || "ok".equalsIgnoreCase(h.getStatus()) ? "LIVE" : "Near-time");
            details.setLastSuccessAt(h.getLastSuccessAt() != null ? VN_OFFSET_FORMATTER.format(h.getLastSuccessAt()) : null);
            details.setLastErrorAt(h.getLastErrorAt() != null ? VN_OFFSET_FORMATTER.format(h.getLastErrorAt()) : null);
            healthMap.put(h.getPlatform(), details);
        }
        // Ensure default map values if empty
        List.of("pancakePos", "pancakeAds").forEach(p -> {
            if (!healthMap.containsKey(p)) {
                DashboardDataDto.PlatformHealthDetailsDto details = new DashboardDataDto.PlatformHealthDetailsDto();
                details.setStatus("ok");
                details.setLabel("pancakeAds".equals(p) ? "Near-time" : "LIVE");
                healthMap.put(p, details);
            }
        });
        dto.setPlatformHealth(healthMap);

        // 2. Fetch today's metrics
        List<DailyMetrics> dailyMetrics = dailyMetricsFuture.join();
        
        BigDecimal revenueToday = BigDecimal.ZERO;
        int ordersToday = 0;
        BigDecimal adSpendToday = BigDecimal.ZERO;
        int returnOrdersToday = 0;
        int totalCancelledToday = 0;

        BigDecimal facebookSpend = BigDecimal.ZERO;
        BigDecimal tiktokSpend = BigDecimal.ZERO;
        BigDecimal shopeeSpend = BigDecimal.ZERO;

        Map<String, BigDecimal> channelRevenueMap = new HashMap<>();
        channelRevenueMap.put("facebook", BigDecimal.ZERO);
        channelRevenueMap.put("instagram", BigDecimal.ZERO);
        channelRevenueMap.put("shopee", BigDecimal.ZERO);
        channelRevenueMap.put("tiktok-shop", BigDecimal.ZERO);
        channelRevenueMap.put("haravan", BigDecimal.ZERO);
        channelRevenueMap.put("pos", BigDecimal.ZERO);
        channelRevenueMap.put("pancake", BigDecimal.ZERO);

        for (DailyMetrics row : dailyMetrics) {
            BigDecimal netRev = row.getNetRevenue();
            int ords = row.getOrderCount();
            BigDecimal spend = row.getAdSpend();

            revenueToday = revenueToday.add(netRev);
            ordersToday += ords;
            adSpendToday = adSpendToday.add(spend);
            returnOrdersToday += row.getRefundCount();
            totalCancelledToday += row.getCancelledCount();

            String platform = row.getPlatform();
            if ("meta-ads".equals(platform) || "facebook-ads".equals(platform)) {
                facebookSpend = facebookSpend.add(spend);
            } else if ("tiktok-ads".equals(platform)) {
                tiktokSpend = tiktokSpend.add(spend);
            } else if ("shopee-ads".equals(platform)) {
                shopeeSpend = shopeeSpend.add(spend);
            } else if (channelRevenueMap.containsKey(platform)) {
                channelRevenueMap.put(platform, channelRevenueMap.get(platform).add(netRev));
            }
        }

        dto.setRevenueToday(revenueToday);
        dto.setOrdersToday(ordersToday);
        dto.setAdSpendToday(adSpendToday);
        dto.setReturnOrdersToday(returnOrdersToday);

        // Calculate cancel rate
        int totalActiveAndCancelled = ordersToday + totalCancelledToday;
        if (totalActiveAndCancelled > 0) {
            dto.setCancelRate(BigDecimal.valueOf((double) totalCancelledToday * 100 / totalActiveAndCancelled).setScale(2, RoundingMode.HALF_UP));
        } else {
            dto.setCancelRate(BigDecimal.ZERO);
        }

        // Spend Breakdown
        dto.setFacebookAdsSpend(facebookSpend);
        dto.setTiktokAdsSpend(tiktokSpend);
        dto.setShopeeAdsSpend(shopeeSpend);

        // ROAS, MER, Average Order Value
        if (adSpendToday.compareTo(BigDecimal.ZERO) > 0) {
            dto.setEstimatedRoas(revenueToday.divide(adSpendToday, 2, RoundingMode.HALF_UP));
            dto.setEstimatedMer(revenueToday.divide(adSpendToday, 2, RoundingMode.HALF_UP));
        } else {
            dto.setEstimatedRoas(BigDecimal.ZERO);
            dto.setEstimatedMer(BigDecimal.ZERO);
        }

        // Fetch yesterday's metrics to compute change percentages
        List<DailyMetrics> yesterdayMetrics = yesterdayMetricsFuture.join();

        BigDecimal revenueYesterday = BigDecimal.ZERO;
        int ordersYesterday = 0;
        BigDecimal adSpendYesterday = BigDecimal.ZERO;

        Map<String, BigDecimal> yesterdayChannelRevMap = new HashMap<>();
        yesterdayChannelRevMap.put("facebook", BigDecimal.ZERO);
        yesterdayChannelRevMap.put("instagram", BigDecimal.ZERO);
        yesterdayChannelRevMap.put("shopee", BigDecimal.ZERO);
        yesterdayChannelRevMap.put("tiktok-shop", BigDecimal.ZERO);
        yesterdayChannelRevMap.put("haravan", BigDecimal.ZERO);
        yesterdayChannelRevMap.put("pos", BigDecimal.ZERO);
        yesterdayChannelRevMap.put("pancake", BigDecimal.ZERO);

        int refundCountYesterday = 0;
        int cancelledCountYesterday = 0;

        for (DailyMetrics row : yesterdayMetrics) {
            revenueYesterday = revenueYesterday.add(row.getNetRevenue());
            ordersYesterday += row.getOrderCount();
            adSpendYesterday = adSpendYesterday.add(row.getAdSpend());
            refundCountYesterday += row.getRefundCount();
            cancelledCountYesterday += row.getCancelledCount();
            
            String platform = row.getPlatform();
            if (yesterdayChannelRevMap.containsKey(platform)) {
                yesterdayChannelRevMap.put(platform, yesterdayChannelRevMap.get(platform).add(row.getNetRevenue()));
            }
        }

        BigDecimal aovYesterday = BigDecimal.ZERO;
        int activeOrdersYesterday = ordersYesterday - refundCountYesterday;
        if (activeOrdersYesterday > 0) {
            aovYesterday = revenueYesterday.divide(BigDecimal.valueOf(activeOrdersYesterday), 2, RoundingMode.HALF_UP);
        }

        BigDecimal aovToday = BigDecimal.ZERO;
        int activeOrdersToday = ordersToday - returnOrdersToday;
        if (activeOrdersToday > 0) {
            aovToday = revenueToday.divide(BigDecimal.valueOf(activeOrdersToday), 2, RoundingMode.HALF_UP);
        }
        dto.setAverageOrderValue(aovToday.setScale(0, RoundingMode.HALF_UP));

        dto.setRevenueTodayChangePercent(calculateChangePercent(revenueToday, revenueYesterday));
        dto.setOrdersTodayChangePercent(calculateChangePercent(BigDecimal.valueOf(ordersToday), BigDecimal.valueOf(ordersYesterday)));
        dto.setAverageOrderValueChangePercent(calculateChangePercent(aovToday, aovYesterday));
        dto.setAdSpendTodayChangePercent(calculateChangePercent(adSpendToday, adSpendYesterday));

        // Fetch Order Status Counts for this specific date & shop
        List<Order> dateOrders = dateOrdersFuture.join();

        Map<String, Integer> statusCounts = new HashMap<>();
        statusCounts.put("completed", 0);
        statusCounts.put("processing", 0);
        statusCounts.put("pending", 0);
        statusCounts.put("cancelled", 0);
        statusCounts.put("refunded", 0);

        for (Order o : dateOrders) {
            String normStatus = o.getNormalizedStatus();
            if (statusCounts.containsKey(normStatus)) {
                statusCounts.put(normStatus, statusCounts.get(normStatus) + 1);
            }
        }
        dto.setOrderStatusCounts(statusCounts);

        // Channel shares
        List<DashboardDataDto.RevenueByChannelItemDto> revenueByChannel = new ArrayList<>();
        BigDecimal finalRevenueToday = revenueToday;
        channelRevenueMap.forEach((platform, rev) -> {
            if (rev.compareTo(BigDecimal.ZERO) > 0) {
                DashboardDataDto.RevenueByChannelItemDto item = new DashboardDataDto.RevenueByChannelItemDto();
                item.setPlatform(platform);
                item.setLabel("facebook".equals(platform) ? "Facebook"
                             : "instagram".equals(platform) ? "Instagram"
                             : "shopee".equals(platform) ? "Shopee"
                             : "tiktok-shop".equals(platform) ? "TikTok Shop"
                             : "haravan".equals(platform) ? "Haravan (Web/App)"
                             : "pos".equals(platform) || "pancake".equals(platform) ? "Pancake POS"
                             : "Pancake POS");
                item.setRevenue(rev);
                if (finalRevenueToday.compareTo(BigDecimal.ZERO) > 0) {
                    item.setShare(rev.multiply(BigDecimal.valueOf(100)).divide(finalRevenueToday, 1, RoundingMode.HALF_UP).doubleValue());
                } else {
                    item.setShare(0.0);
                }
                BigDecimal prevRev = yesterdayChannelRevMap.getOrDefault(platform, BigDecimal.ZERO);
                BigDecimal change = calculateChangePercent(rev, prevRev);
                item.setChangePercent(change.doubleValue());
                revenueByChannel.add(item);
            }
        });
        revenueByChannel.sort(Comparator.comparing(DashboardDataDto.RevenueByChannelItemDto::getRevenue).reversed());
        dto.setRevenueByChannel(revenueByChannel);

        // Ad Cost Breakdown
        List<DashboardDataDto.AdCostBreakdownItemDto> adCostBreakdown = new ArrayList<>();
        BigDecimal finalAdSpend = adSpendToday;
        Map.of("facebook-ads", facebookSpend, "tiktok-ads", tiktokSpend, "shopee-ads", shopeeSpend).forEach((platform, spend) -> {
            DashboardDataDto.AdCostBreakdownItemDto item = new DashboardDataDto.AdCostBreakdownItemDto();
            item.setPlatform(platform);
            item.setLabel("facebook-ads".equals(platform) ? "Facebook Ads" : "tiktok-ads".equals(platform) ? "TikTok Ads" : "Shopee Ads");
            item.setSpend(spend);
            if (finalAdSpend.compareTo(BigDecimal.ZERO) > 0) {
                item.setShare(spend.multiply(BigDecimal.valueOf(100)).divide(finalAdSpend, 1, RoundingMode.HALF_UP).doubleValue());
            } else {
                item.setShare(0.0);
            }
            adCostBreakdown.add(item);
        });
        dto.setAdCostBreakdown(adCostBreakdown);

        // 3. Hourly Trends
        List<HourlyMetrics> hourlyMetrics = hourlyMetricsFuture.join();
        List<HourlyMetrics> yesterdayHourlyMetrics = yesterdayHourlyMetricsFuture.join();
        
        Map<Integer, DashboardDataDto.HourlyRevenueItemDto> hourlyRevMap = new LinkedHashMap<>();
        Map<Integer, DashboardDataDto.HourlyAdCostDto> hourlyAdMap = new LinkedHashMap<>();

        for (int i = 0; i < 24; i++) {
            String hrStr = String.format("%02d:00", i);

            DashboardDataDto.HourlyRevenueItemDto rItem = new DashboardDataDto.HourlyRevenueItemDto();
            rItem.setHour(hrStr);
            rItem.setTodayRevenue(BigDecimal.ZERO);
            rItem.setYesterdayRevenue(BigDecimal.ZERO); // dynamic comparison, initialized to ZERO
            hourlyRevMap.put(i, rItem);

            DashboardDataDto.HourlyAdCostDto aItem = new DashboardDataDto.HourlyAdCostDto();
            aItem.setHour(hrStr);
            aItem.setFacebookAdsHourlySpend(BigDecimal.ZERO);
            aItem.setTiktokAdsHourlySpend(BigDecimal.ZERO);
            aItem.setShopeeAdsHourlySpend(BigDecimal.ZERO);
            hourlyAdMap.put(i, aItem);
        }

        for (HourlyMetrics row : hourlyMetrics) {
            ZonedDateTime zdt = row.getHour().atZone(VN_ZONE);
            int localHour = zdt.getHour();

            if (hourlyRevMap.containsKey(localHour)) {
                DashboardDataDto.HourlyRevenueItemDto rItem = hourlyRevMap.get(localHour);
                rItem.setTodayRevenue(rItem.getTodayRevenue().add(row.getNetRevenue()));
            }

            if (hourlyAdMap.containsKey(localHour)) {
                DashboardDataDto.HourlyAdCostDto aItem = hourlyAdMap.get(localHour);
                String platform = row.getPlatform();
                if ("meta-ads".equals(platform) || "facebook-ads".equals(platform)) {
                    aItem.setFacebookAdsHourlySpend(aItem.getFacebookAdsHourlySpend().add(row.getAdSpend()));
                } else if ("tiktok-ads".equals(platform)) {
                    aItem.setTiktokAdsHourlySpend(aItem.getTiktokAdsHourlySpend().add(row.getAdSpend()));
                } else if ("shopee-ads".equals(platform)) {
                    aItem.setShopeeAdsHourlySpend(aItem.getShopeeAdsHourlySpend().add(row.getAdSpend()));
                }
            }
        }

        for (HourlyMetrics row : yesterdayHourlyMetrics) {
            ZonedDateTime zdt = row.getHour().atZone(VN_ZONE);
            int localHour = zdt.getHour();

            if (hourlyRevMap.containsKey(localHour)) {
                DashboardDataDto.HourlyRevenueItemDto rItem = hourlyRevMap.get(localHour);
                rItem.setYesterdayRevenue(rItem.getYesterdayRevenue().add(row.getNetRevenue()));
            }
        }

        dto.setHourlyRevenue(new ArrayList<>(hourlyRevMap.values()));
        dto.setHourlyAdCost(new ArrayList<>(hourlyAdMap.values()));

        // 4. Realtime Orders
        List<Order> latestOrders = latestOrdersFuture.join();
        
        List<DashboardDataDto.RealtimeOrderDto> realtimeOrders = latestOrders.stream()
                .sorted(Comparator.comparing(this::resolveBusinessTime).reversed())
                .limit(10)
                .map(o -> {
                    DashboardDataDto.RealtimeOrderDto r = new DashboardDataDto.RealtimeOrderDto();
                    r.setId(o.getId().toString());
                    r.setCreatedAt(resolveBusinessTime(o).atZone(VN_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                    r.setOrderCode(o.getPlatformOrderId());
                    r.setCustomerDisplayName(o.getCustomerName() != null ? o.getCustomerName() : "Khách Hàng");
                    String platform = orderSourceChannel(o);
                    r.setPlatform(platform);
                    r.setOrderValue(o.getNetRevenue());
                    return r;
                }).collect(Collectors.toList());
        dto.setRealtimeOrders(realtimeOrders);

        // 5. Top Products
        List<Object[]> rawProducts = rawProductsFuture.join();

        List<DashboardDataDto.TopProductItemDto> topProducts = new ArrayList<>();
        int rank = 1;
        for (Object[] row : rawProducts) {
            String name = row[0] != null ? (String) row[0] : "Sản phẩm";
            Long quantity = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            BigDecimal revenue = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;

            DashboardDataDto.TopProductItemDto prod = new DashboardDataDto.TopProductItemDto();
            prod.setRank(rank++);
            prod.setProductName(name);
            prod.setOrders(quantity.intValue());
            prod.setRevenue(revenue);
            topProducts.add(prod);

            if (topProducts.size() >= 5) {
                break;
            }
        }

        dto.setTopProducts(topProducts);

        // Top Products By Channel
        List<Object[]> rawItems = rawItemsFuture.join();

        Map<String, TopProductByChannelAgg> aggMap = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        for (Object[] row : rawItems) {
            String prodName = row[0] != null ? (String) row[0] : "Sản phẩm";
            String plat = row[1] != null ? (String) row[1] : "unknown";
            String rawD = row[2] != null ? (String) row[2] : null;
            Integer qty = row[3] != null ? ((Number) row[3]).intValue() : 0;
            BigDecimal totalP = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;

            String finalPlat = plat;

            String key = prodName + "_" + finalPlat;
            TopProductByChannelAgg agg = aggMap.computeIfAbsent(key, k -> new TopProductByChannelAgg(prodName, finalPlat));
            agg.orders += qty;
            agg.revenue = agg.revenue.add(totalP);
        }

        List<DashboardDataDto.TopProductByChannelItemDto> topProductsByChannel = aggMap.values().stream()
                .sorted(Comparator.comparing(TopProductByChannelAgg::getRevenue).reversed())
                .limit(10)
                .map(agg -> {
                    DashboardDataDto.TopProductByChannelItemDto item = new DashboardDataDto.TopProductByChannelItemDto();
                    item.setProductName(agg.productName);
                    item.setPlatform(agg.platform);
                    item.setOrders(agg.orders);
                    item.setRevenue(agg.revenue);
                    return item;
                }).collect(Collectors.toList());

        for (int i = 0; i < topProductsByChannel.size(); i++) {
            topProductsByChannel.get(i).setRank(i + 1);
        }
        dto.setTopProductsByChannel(topProductsByChannel);

        // 6. Top Ad Campaigns
        List<AdInsightsHourly> insights = insightsFuture.join();
        Map<String, DashboardDataDto.TopAdCampaignDto> campaignMap = new HashMap<>();
        Map<String, Set<String>> campaignNameToIds = new HashMap<>();

        for (AdInsightsHourly item : insights) {
            String name = item.getCampaignName() != null ? item.getCampaignName() : "Chiến dịch";
            DashboardDataDto.TopAdCampaignDto c = campaignMap.computeIfAbsent(name, n -> {
                DashboardDataDto.TopAdCampaignDto dtoCamp = new DashboardDataDto.TopAdCampaignDto();
                dtoCamp.setCampaignName(n);
                dtoCamp.setPlatform(item.getPlatform());
                dtoCamp.setSpend(BigDecimal.ZERO);
                dtoCamp.setAttributedOrders(0);
                dtoCamp.setRoas(BigDecimal.ZERO);
                return dtoCamp;
            });
            c.setSpend(c.getSpend().add(item.getSpend()));

            if (item.getCampaignId() != null && !item.getCampaignId().isEmpty()) {
                campaignNameToIds.computeIfAbsent(name, k -> new HashSet<>()).add(item.getCampaignId());
            }

            // Extract ad-level IDs from raw_data to build ad_id → campaign mapping
            if (item.getRawData() != null && !item.getRawData().isEmpty()) {
                try {
                    JsonNode rawNode = objectMapper.readTree(item.getRawData());
                    if (rawNode.has("ad_ids") && rawNode.get("ad_ids").isArray()) {
                        for (JsonNode adIdNode : rawNode.get("ad_ids")) {
                            String adId = adIdNode.asText().trim();
                            if (!adId.isEmpty()) {
                                campaignNameToIds.computeIfAbsent(name, k -> new HashSet<>()).add(adId);
                            }
                        }
                    }
                    // Also check for direct "id" field (ad-level ID)
                    if (rawNode.has("id") && !rawNode.get("id").isNull()) {
                        String adId = rawNode.get("id").asText().trim();
                        if (!adId.isEmpty()) {
                            campaignNameToIds.computeIfAbsent(name, k -> new HashSet<>()).add(adId);
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        Map<String, BigDecimal> campaignRevenueMap = new HashMap<>();

        for (Order o : dateOrders) {
            String orderUtmCampaign = "";
            String orderUtmId = "";
            String orderAdId = "";

            if (o.getRawData() != null && !o.getRawData().isEmpty()) {
                try {
                    JsonNode node = objectMapper.readTree(o.getRawData());
                    if (node.has("p_utm_campaign") && !node.get("p_utm_campaign").isNull()) {
                        orderUtmCampaign = node.get("p_utm_campaign").asText().trim();
                    }
                    if (node.has("p_utm_id") && !node.get("p_utm_id").isNull()) {
                        orderUtmId = node.get("p_utm_id").asText().trim();
                    }
                    if (node.has("ad_id") && !node.get("ad_id").isNull()) {
                        orderAdId = node.get("ad_id").asText().trim();
                    }
                } catch (Exception e) {
                    // ignore
                }
            }

            if (orderUtmCampaign.isEmpty() && orderUtmId.isEmpty() && orderAdId.isEmpty()) {
                continue;
            }

            String matchedCampaignName = null;
            for (Map.Entry<String, Set<String>> entry : campaignNameToIds.entrySet()) {
                String name = entry.getKey();
                Set<String> ids = entry.getValue();

                if ((!orderUtmId.isEmpty() && ids.contains(orderUtmId)) ||
                    (!orderUtmCampaign.isEmpty() && ids.contains(orderUtmCampaign)) ||
                    (!orderAdId.isEmpty() && ids.contains(orderAdId))) {
                    matchedCampaignName = name;
                    break;
                }

                if (!orderUtmCampaign.isEmpty() && name.equalsIgnoreCase(orderUtmCampaign)) {
                    matchedCampaignName = name;
                    break;
                }
            }

            if (matchedCampaignName != null) {
                DashboardDataDto.TopAdCampaignDto c = campaignMap.get(matchedCampaignName);
                c.setAttributedOrders(c.getAttributedOrders() + 1);

                BigDecimal currentRev = campaignRevenueMap.getOrDefault(matchedCampaignName, BigDecimal.ZERO);
                campaignRevenueMap.put(matchedCampaignName, currentRev.add(o.getNetRevenue()));
            }
        }

        List<DashboardDataDto.TopAdCampaignDto> topAdCampaigns = campaignMap.values().stream()
                .sorted(Comparator.comparing(DashboardDataDto.TopAdCampaignDto::getSpend).reversed())
                .limit(5)
                .map(c -> {
                    BigDecimal rev = campaignRevenueMap.getOrDefault(c.getCampaignName(), BigDecimal.ZERO);
                    if (c.getSpend().compareTo(BigDecimal.ZERO) > 0) {
                        c.setRoas(rev.divide(c.getSpend(), 2, RoundingMode.HALF_UP));
                    } else {
                        c.setRoas(BigDecimal.ZERO);
                    }
                    return c;
                }).collect(Collectors.toList());

        dto.setTopAdCampaigns(topAdCampaigns);

        // 7. KPI Sparklines (generated based on actual hourly metrics for this day)
        double[] hrRev = new double[24];
        double[] hrOrd = new double[24];
        double[] hrSpend = new double[24];

        for (HourlyMetrics row : hourlyMetrics) {
            ZonedDateTime zdt = row.getHour().atZone(VN_ZONE);
            int hr = zdt.getHour();
            if (hr >= 0 && hr < 24) {
                if (row.getNetRevenue() != null) {
                    hrRev[hr] += row.getNetRevenue().doubleValue();
                }
                if (row.getOrderCount() != null) {
                    hrOrd[hr] += row.getOrderCount().doubleValue();
                }
                if (row.getAdSpend() != null) {
                    hrSpend[hr] += row.getAdSpend().doubleValue();
                }
            }
        }

        List<DashboardDataDto.KpiSparkPointDto> revenuePoints = new ArrayList<>();
        List<DashboardDataDto.KpiSparkPointDto> ordersPoints = new ArrayList<>();
        List<DashboardDataDto.KpiSparkPointDto> aovPoints = new ArrayList<>();
        List<DashboardDataDto.KpiSparkPointDto> adSpendPoints = new ArrayList<>();

        for (int i = 0; i < 24; i++) {
            String label = String.format("%02d:00", i);
            double rev = hrRev[i];
            double ord = hrOrd[i];
            double spend = hrSpend[i];
            double aov = ord > 0 ? rev / ord : 0.0;

            revenuePoints.add(new DashboardDataDto.KpiSparkPointDto(label, rev, 0.0));
            ordersPoints.add(new DashboardDataDto.KpiSparkPointDto(label, ord, 0.0));
            aovPoints.add(new DashboardDataDto.KpiSparkPointDto(label, aov, 0.0));
            adSpendPoints.add(new DashboardDataDto.KpiSparkPointDto(label, spend, 0.0));
        }

        Map<String, List<DashboardDataDto.KpiSparkPointDto>> kpiSparklines = new HashMap<>();
        kpiSparklines.put("revenueToday", revenuePoints);
        kpiSparklines.put("ordersToday", ordersPoints);
        kpiSparklines.put("averageOrderValue", aovPoints);
        kpiSparklines.put("adSpendToday", adSpendPoints);
        dto.setKpiSparklines(kpiSparklines);

        dto.setLastUpdatedAt(ZonedDateTime.now(VN_ZONE).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        if (isPastDate) {
            dashboardCache.put(cacheKey, dto);
        }

        return dto;
    }

    private List<DashboardDataDto.KpiSparkPointDto> generateSparkline(double baseVal, boolean isRate) {
        List<DashboardDataDto.KpiSparkPointDto> points = new ArrayList<>();
        int hours = 24;
        for (int i = 0; i < hours; i++) {
            String label = String.format("%02d:00", i);
            double val;
            if (isRate) {
                val = baseVal * (0.6 + Math.sin(i / 3.0) * 0.3 + (Math.random() - 0.5) * 0.05);
            } else {
                double progress = (i + 1) / (double) hours;
                val = baseVal * progress + (Math.random() - 0.5) * baseVal * 0.05;
            }
            val = Math.max(0, Math.round(val));
            double prevVal = Math.round(val * (0.9 + Math.random() * 0.18));
            points.add(new DashboardDataDto.KpiSparkPointDto(label, val, prevVal));
        }
        return points;
    }

    private BigDecimal calculateChangePercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            if (current != null && current.compareTo(BigDecimal.ZERO) > 0) {
                return BigDecimal.valueOf(100.00); // +100%
            }
            return BigDecimal.ZERO;
        }
        if (current == null) {
            return BigDecimal.valueOf(-100.00); // -100%
        }
        BigDecimal diff = current.subtract(previous);
        return diff.multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP);
    }

    private static class TopProductByChannelAgg {
        String productName;
        String platform;
        int orders = 0;
        BigDecimal revenue = BigDecimal.ZERO;

        TopProductByChannelAgg(String productName, String platform) {
            this.productName = productName;
            this.platform = platform;
        }

        public BigDecimal getRevenue() {
            return revenue;
        }
    }
}
