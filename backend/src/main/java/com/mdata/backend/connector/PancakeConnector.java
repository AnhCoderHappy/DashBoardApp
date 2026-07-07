package com.mdata.backend.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdata.backend.entity.Order;
import com.mdata.backend.entity.OrderItem;
import com.mdata.backend.repository.OrderItemRepository;
import com.mdata.backend.repository.OrderRepository;
import com.mdata.backend.repository.PlatformConnectionRepository;
import com.mdata.backend.repository.AdInsightsHourlyRepository;
import com.mdata.backend.service.MetricsService;
import com.mdata.backend.service.TokenService;
import com.mdata.backend.entity.PlatformConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class PancakeConnector implements PlatformConnector {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PlatformConnectionRepository connectionRepository;
    private final TokenService tokenService;
    private final AdInsightsHourlyRepository adInsightsHourlyRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean mockPlatforms;

    @Value("${PANCAKE_API_BASE:https://pos.pages.fm/api/v1}")
    private String apiBase;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public PancakeConnector(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PlatformConnectionRepository connectionRepository,
            TokenService tokenService,
            AdInsightsHourlyRepository adInsightsHourlyRepository,
            @Value("${MOCK_PLATFORMS:false}") String mockPlatforms
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.connectionRepository = connectionRepository;
        this.tokenService = tokenService;
        this.adInsightsHourlyRepository = adInsightsHourlyRepository;
        this.mockPlatforms = "true".equalsIgnoreCase(mockPlatforms);
    }

    @Override
    public String getPlatform() {
        return "pancake";
    }

    @Override
    public void refreshToken(UUID connectionId) {
        System.out.println("[Pancake] Token refresh requested (noop for API key).");
    }

    public Map<String, String> fetchShopInfo(String apiKey) throws Exception {
        if (mockPlatforms) {
            return Map.of("id", "MOCK-SHOP-" + System.currentTimeMillis(), "name", "Mock Pancake Shop");
        }

        String url = apiBase + "/shops?api_key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Invalid API Key. Pancake API returned HTTP " + response.statusCode());
        }

        var jsonNode = objectMapper.readTree(response.body());
        if (jsonNode.has("success") && jsonNode.get("success").asBoolean() && jsonNode.has("shops")) {
            var shops = jsonNode.get("shops");
            if (shops.isArray() && shops.size() > 0) {
                var firstShop = shops.get(0);
                String id = firstShop.get("id").asText();
                String name = firstShop.has("name") ? firstShop.get("name").asText() : "Pancake Shop " + id;
                return Map.of("id", id, "name", name);
            }
        }
        throw new RuntimeException("No shops found for this API Key.");
    }

    @Override
    public boolean testConnection(UUID connectionId) throws Exception {
        PlatformConnection conn = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
        String shopId = conn.getShopId();
        String apiKey = tokenService.getConnectionToken(connectionId).getAccessToken();

        if (mockPlatforms) {
            return true;
        }

        String url = apiBase + "/shops/" + shopId + "/orders?api_key=" + apiKey + "&page=1&limit=1";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200;
    }

    @Override
    public void syncOrders(UUID connectionId, Instant since) throws Exception {
        PlatformConnection conn = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
        String shopId = conn.getShopId();
        String apiKey = tokenService.getConnectionToken(connectionId).getAccessToken();

        if (mockPlatforms) {
            System.out.println("[Pancake] Syncing mock orders for shop " + shopId + "...");
            generateMockOrders(connectionId, since);
            return;
        }

        Instant startInstant = since != null ? since : Instant.now().minus(java.time.Duration.ofDays(7));
        long startSeconds = startInstant.getEpochSecond();
        long endSeconds = Instant.now().getEpochSecond();

        String url = apiBase + "/shops/" + shopId + "/orders?api_key=" + apiKey 
                + "&page_size=100&updateStatus=inserted_at&startDateTime=" + startSeconds + "&endDateTime=" + endSeconds;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Pancake API error HTTP " + response.statusCode() + ": " + response.body());
        }

        System.out.println("[Pancake] Orders fetched successfully for shop " + shopId);
        try { java.nio.file.Files.writeString(java.nio.file.Paths.get("lillie_orders.json"), response.body()); } catch(Exception e) {}
        
        var jsonNode = objectMapper.readTree(response.body());
        if (jsonNode.has("data") && jsonNode.get("data").isArray()) {
            var dataArray = jsonNode.get("data");
            for (var node : dataArray) {
                saveOrderFromJsonNode(node, connectionId);
            }
        }
    }

    public void processSingleOrderPayload(String payload, UUID connectionId) throws Exception {
        var node = objectMapper.readTree(payload);
        if (node.has("data")) {
            node = node.get("data");
        }
        if (node.isArray()) {
            for (var orderNode : node) {
                saveOrderFromJsonNode(orderNode, connectionId);
            }
        } else {
            saveOrderFromJsonNode(node, connectionId);
        }
    }

    public void saveOrderFromJsonNode(com.fasterxml.jackson.databind.JsonNode node, UUID connectionId) {
        try {
            String platformOrderId = node.has("id") ? node.get("id").asText() : "UNKNOWN_" + System.currentTimeMillis();
            String status = node.has("status") ? node.get("status").asText() : "UNKNOWN";
            
            Order order = orderRepository.findByPlatformAndPlatformOrderId("pancake", platformOrderId)
                    .orElseGet(Order::new);
                    
            order.setPlatform("pancake");
            order.setPlatformOrderId(platformOrderId);
            order.setConnectionId(connectionId);
            order.setStatus(status);
            order.setNormalizedStatus(MetricsService.normalizeOrderStatus("pancake", status));
            
            BigDecimal total = BigDecimal.ZERO;
            if (node.has("total_price")) total = new BigDecimal(node.get("total_price").asText());
            else if (node.has("total_amount")) total = new BigDecimal(node.get("total_amount").asText());
            else if (node.has("money_to_collect")) total = new BigDecimal(node.get("money_to_collect").asText());
                              
            order.setGrossRevenue(total);
            order.setNetRevenue(total);
            order.setCurrency("VND");
            order.setRawData(node.toString());

            // Try parsing inserted_at
            if (node.has("inserted_at") && !node.get("inserted_at").isNull()) {
                try {
                    String insertedAtStr = node.get("inserted_at").asText();
                    if (insertedAtStr.contains(".")) {
                        insertedAtStr = insertedAtStr.substring(0, insertedAtStr.indexOf("."));
                    }
                    java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(insertedAtStr);
                    order.setCreatedAtPlatform(ldt.atZone(java.time.ZoneId.of("UTC")).toInstant());
                } catch (Exception ex) {
                    order.setCreatedAtPlatform(Instant.now());
                }
            } else if (order.getCreatedAtPlatform() == null) {
                order.setCreatedAtPlatform(Instant.now());
            }
            
            if (node.has("customer") && node.get("customer").has("name")) {
                order.setCustomerName(node.get("customer").get("name").asText());
            }

            order.setUpdatedAtPlatform(Instant.now());
            order.setUpdatedAt(Instant.now());
            
            Order savedOrder = orderRepository.save(order);

            // Sync Order Items
            if (node.has("items") && node.get("items").isArray()) {
                orderItemRepository.deleteByOrderId(savedOrder.getId());
                var itemsArray = node.get("items");
                for (var itemNode : itemsArray) {
                    try {
                        OrderItem item = new OrderItem();
                        item.setOrderId(savedOrder.getId());
                        item.setPlatformProductId(itemNode.has("product_id") ? itemNode.get("product_id").asText() : "");
                        item.setPlatformSkuId(itemNode.has("variation_id") ? itemNode.get("variation_id").asText() : "");
                        
                        String productName = "Sản Phẩm";
                        String sku = "";
                        BigDecimal price = BigDecimal.ZERO;
                        
                        if (itemNode.has("variation_info") && !itemNode.get("variation_info").isNull()) {
                            var varInfo = itemNode.get("variation_info");
                            if (varInfo.has("name")) {
                                productName = varInfo.get("name").asText();
                            }
                            if (varInfo.has("display_id")) {
                                sku = varInfo.get("display_id").asText();
                            } else if (varInfo.has("barcode")) {
                                sku = varInfo.get("barcode").asText();
                            }
                            if (varInfo.has("retail_price")) {
                                price = new BigDecimal(varInfo.get("retail_price").asText());
                            }
                        }
                        
                        int quantity = itemNode.has("quantity") ? itemNode.get("quantity").asInt() : 1;
                        BigDecimal discountEach = BigDecimal.ZERO;
                        if (itemNode.has("discount_each_product") && !itemNode.get("discount_each_product").isNull()) {
                            discountEach = new BigDecimal(itemNode.get("discount_each_product").asText());
                        }
                        
                        BigDecimal netPrice = price.subtract(discountEach);
                        
                        item.setProductName(productName);
                        item.setSku(sku);
                        item.setQuantity(quantity);
                        item.setUnitPrice(netPrice);
                        item.setTotalPrice(netPrice.multiply(BigDecimal.valueOf(quantity)));
                        item.setRawData(itemNode.toString());
                        item.setCreatedAt(Instant.now());
                        
                        orderItemRepository.save(item);
                    } catch (Exception itemEx) {
                        System.err.println("Failed to parse order item: " + itemEx.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse order node: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void syncAdsInsights(UUID connectionId, Instant since) throws Exception {
        PlatformConnection conn = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found: " + connectionId));
        String shopId = conn.getShopId();
        String apiKey = tokenService.getConnectionToken(connectionId).getAccessToken();

        if (mockPlatforms) {
            System.out.println("[Pancake] Syncing mock ads insights for shop " + shopId + "...");
            return;
        }

        String url = apiBase + "/shops/" + shopId + "/ads_manager/ads_v2?api_key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Pancake API error HTTP " + response.statusCode() + ": " + response.body());
        }

        System.out.println("[Pancake] Ads insights fetched successfully for shop " + shopId);
        
        var jsonNode = objectMapper.readTree(response.body());
        if (jsonNode.has("data") && jsonNode.get("data").isArray()) {
            var dataArray = jsonNode.get("data");
            
            // Map to aggregate by campaign_id
            Map<String, CampaignAggregation> campaignMap = new HashMap<>();

            for (var node : dataArray) {
                try {
                    String campaignId = "";
                    String campaignName = "";
                    if (node.has("ad_campaign") && !node.get("ad_campaign").isNull()) {
                        var campaignNode = node.get("ad_campaign");
                        campaignId = campaignNode.has("id") ? campaignNode.get("id").asText() : "";
                        campaignName = campaignNode.has("name") ? campaignNode.get("name").asText() : "";
                    }

                    if (campaignId.isEmpty()) {
                        continue;
                    }

                    String adAccountId = "";
                    if (node.has("ad_account") && !node.get("ad_account").isNull()) {
                        adAccountId = node.get("ad_account").has("id") ? node.get("ad_account").get("id").asText() : "";
                    }

                    Instant createdTime = Instant.now();
                    if (node.has("created_time") && !node.get("created_time").isNull()) {
                        try {
                            String ctStr = node.get("created_time").asText();
                            if (ctStr.contains(".")) {
                                ctStr = ctStr.substring(0, ctStr.indexOf("."));
                            }
                            java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(ctStr);
                            createdTime = ldt.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
                        } catch (Exception ex) {
                            // ignore
                        }
                    }

                    BigDecimal spend = BigDecimal.ZERO;
                    int impressions = 0;
                    int clicks = 0;
                    int reach = 0;

                    if (node.has("insights") && !node.get("insights").isNull()) {
                        var insNode = node.get("insights");
                        spend = safeBigDecimal(insNode, "spend");
                        impressions = safeInt(insNode, "impressions");
                        clicks = safeInt(insNode, "clicks");
                        reach = safeInt(insNode, "reach");
                    }

                    final String finalCampaignId = campaignId;
                    CampaignAggregation agg = campaignMap.computeIfAbsent(finalCampaignId, k -> {
                        CampaignAggregation newAgg = new CampaignAggregation();
                        newAgg.campaignId = finalCampaignId;
                        return newAgg;
                    });

                    agg.adAccountId = adAccountId;
                    agg.campaignName = campaignName;
                    if (createdTime.isBefore(agg.earliestCreatedTime)) {
                        agg.earliestCreatedTime = createdTime;
                    }
                    agg.totalSpend = agg.totalSpend.add(spend);
                    agg.totalImpressions += impressions;
                    agg.totalClicks += clicks;
                    agg.totalReach += reach;
                    agg.rawNode = node;

                    // Collect ad-level ID for campaign attribution
                    if (node.has("id") && !node.get("id").isNull()) {
                        String adLevelId = node.get("id").asText().trim();
                        if (!adLevelId.isEmpty()) {
                            agg.adIds.add(adLevelId);
                        }
                    }

                } catch (Exception e) {
                    System.err.println("Failed to process ad insight node: " + e.getMessage());
                }
            }

            // Save aggregated campaigns
            for (CampaignAggregation agg : campaignMap.values()) {
                try {
                    boolean hasExistingRecords = adInsightsHourlyRepository.existsByPlatformAndAdAccountIdAndCampaignId(
                            "facebook-ads", agg.adAccountId, agg.campaignId);

                    Instant hour;
                    if (!hasExistingRecords) {
                        hour = agg.earliestCreatedTime.truncatedTo(ChronoUnit.HOURS);
                    } else {
                        hour = Instant.now().truncatedTo(ChronoUnit.HOURS);
                    }

                    var existingOpt = adInsightsHourlyRepository.findByPlatformAndAdAccountIdAndCampaignIdAndHour(
                            "facebook-ads", agg.adAccountId, agg.campaignId, hour);

                    com.mdata.backend.entity.AdInsightsHourly insight = existingOpt.orElseGet(com.mdata.backend.entity.AdInsightsHourly::new);
                    insight.setPlatform("facebook-ads");
                    insight.setAdAccountId(agg.adAccountId);
                    insight.setCampaignId(agg.campaignId);
                    insight.setCampaignName(agg.campaignName);
                    insight.setHour(hour);

                    if (!hasExistingRecords) {
                        insight.setSpend(agg.totalSpend);
                        insight.setImpressions(agg.totalImpressions);
                        insight.setClicks(agg.totalClicks);
                        insight.setReach(agg.totalReach);
                        
                        BigDecimal cpc = BigDecimal.ZERO;
                        if (agg.totalClicks > 0) {
                            cpc = agg.totalSpend.divide(BigDecimal.valueOf(agg.totalClicks), 2, RoundingMode.HALF_UP);
                        }
                        BigDecimal cpm = BigDecimal.ZERO;
                        if (agg.totalImpressions > 0) {
                            cpm = agg.totalSpend.multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(agg.totalImpressions), 2, RoundingMode.HALF_UP);
                        }
                        BigDecimal ctr = BigDecimal.ZERO;
                        if (agg.totalImpressions > 0) {
                            ctr = BigDecimal.valueOf(agg.totalClicks).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(agg.totalImpressions), 2, RoundingMode.HALF_UP);
                        }
                        insight.setCpc(cpc);
                        insight.setCpm(cpm);
                        insight.setCtr(ctr);
                    } else {
                        // Query sums before the current hour
                        BigDecimal sumSpend = adInsightsHourlyRepository.sumSpendBeforeHour("facebook-ads", agg.adAccountId, agg.campaignId, hour);
                        if (sumSpend == null) sumSpend = BigDecimal.ZERO;

                        Long sumImpressions = adInsightsHourlyRepository.sumImpressionsBeforeHour("facebook-ads", agg.adAccountId, agg.campaignId, hour);
                        if (sumImpressions == null) sumImpressions = 0L;

                        Long sumClicks = adInsightsHourlyRepository.sumClicksBeforeHour("facebook-ads", agg.adAccountId, agg.campaignId, hour);
                        if (sumClicks == null) sumClicks = 0L;

                        Long sumReach = adInsightsHourlyRepository.sumReachBeforeHour("facebook-ads", agg.adAccountId, agg.campaignId, hour);
                        if (sumReach == null) sumReach = 0L;

                        // Calculate incremental values
                        BigDecimal spendInc = agg.totalSpend.subtract(sumSpend);
                        if (spendInc.compareTo(BigDecimal.ZERO) < 0) spendInc = BigDecimal.ZERO;

                        int impressionsInc = agg.totalImpressions - sumImpressions.intValue();
                        if (impressionsInc < 0) impressionsInc = 0;

                        int clicksInc = agg.totalClicks - sumClicks.intValue();
                        if (clicksInc < 0) clicksInc = 0;

                        int reachInc = agg.totalReach - sumReach.intValue();
                        if (reachInc < 0) reachInc = 0;

                        // Calculate CPC, CPM, CTR for current hour based on increments
                        BigDecimal cpcInc = BigDecimal.ZERO;
                        if (clicksInc > 0) {
                            cpcInc = spendInc.divide(BigDecimal.valueOf(clicksInc), 2, RoundingMode.HALF_UP);
                        }
                        BigDecimal cpmInc = BigDecimal.ZERO;
                        if (impressionsInc > 0) {
                            cpmInc = spendInc.multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(impressionsInc), 2, RoundingMode.HALF_UP);
                        }
                        BigDecimal ctrInc = BigDecimal.ZERO;
                        if (impressionsInc > 0) {
                            ctrInc = BigDecimal.valueOf(clicksInc).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(impressionsInc), 2, RoundingMode.HALF_UP);
                        }

                        insight.setSpend(spendInc);
                        insight.setImpressions(impressionsInc);
                        insight.setClicks(clicksInc);
                        insight.setReach(reachInc);
                        insight.setCpc(cpcInc);
                        insight.setCpm(cpmInc);
                        insight.setCtr(ctrInc);
                    }

                    // Build raw_data JSON with ad_ids for campaign attribution
                    try {
                        var rawMap = objectMapper.readValue(agg.rawNode.toString(), java.util.Map.class);
                        rawMap.put("ad_ids", new java.util.ArrayList<>(agg.adIds));
                        insight.setRawData(objectMapper.writeValueAsString(rawMap));
                    } catch (Exception ex) {
                        insight.setRawData(agg.rawNode.toString());
                    }
                    insight.setCreatedAt(Instant.now());
                    adInsightsHourlyRepository.save(insight);
                } catch (Exception e) {
                    System.err.println("Failed to save aggregated ad campaign insight: " + e.getMessage());
                }
            }
            System.out.println("[Pancake] Processed " + campaignMap.size() + " aggregated ad campaign insight records.");
        }
    }

    private BigDecimal safeBigDecimal(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            try {
                String val = node.get(field).asText();
                if ("NaN".equalsIgnoreCase(val) || "null".equalsIgnoreCase(val)) {
                    return BigDecimal.ZERO;
                }
                return new BigDecimal(val);
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private int safeInt(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            try {
                return node.get(field).asInt();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    private static class CampaignAggregation {
        String campaignId;
        String adAccountId;
        String campaignName;
        Instant earliestCreatedTime = Instant.now();
        BigDecimal totalSpend = BigDecimal.ZERO;
        int totalImpressions = 0;
        int totalClicks = 0;
        int totalReach = 0;
        com.fasterxml.jackson.databind.JsonNode rawNode;
        Set<String> adIds = new HashSet<>();
    }

    private void generateMockOrders(UUID connectionId, Instant since) {
        Instant threshold = since != null ? since : Instant.now().minus(24, ChronoUnit.HOURS);
        int count = 5 + (int) (Math.random() * 10);
        
        String[] mockPlatforms = {"shopee", "tiktok-shop", "facebook-ads", "haravan"};

        for (int i = 0; i < count; i++) {
            Order order = new Order();
            String platform = mockPlatforms[i % mockPlatforms.length];
            order.setPlatform(platform); // Treat Pancake source as the actual platform it came from
            order.setPlatformOrderId("PCK-" + System.currentTimeMillis() + "-" + i);
            order.setConnectionId(connectionId);
            
            double r = Math.random();
            String status = r < 0.7 ? "PAID" : r < 0.9 ? "CANCELLED" : "REFUNDED";
            order.setStatus(status);
            order.setNormalizedStatus(MetricsService.normalizeOrderStatus(platform, status));

            BigDecimal gross = BigDecimal.valueOf(150000 + Math.random() * 300000);
            BigDecimal discount = BigDecimal.valueOf(Math.random() * 20000);
            BigDecimal shipping = BigDecimal.valueOf(30000);
            order.setGrossRevenue(gross);
            order.setDiscountAmount(discount);
            order.setShippingFee(shipping);
            order.setNetRevenue(gross.subtract(discount));
            order.setCurrency("VND");
            order.setCustomerName(List.of("Nguyen Van A", "Tran Thi B", "Le Van C").get(i % 3));
            order.setCreatedAtPlatform(threshold.plus(i * 2, ChronoUnit.HOURS));
            order.setUpdatedAtPlatform(Instant.now());
            order.setRawData("{\"source\":\"pancake\", \"mock\":true}");
            order.setUpdatedAt(Instant.now());

            Order savedOrder = orderRepository.save(order);

            OrderItem item = new OrderItem();
            item.setOrderId(savedOrder.getId());
            item.setPlatformProductId("prod-pck-1");
            item.setPlatformSkuId("sku-pck-1");
            item.setSku("PCK-TEE-BLK");
            item.setProductName("Áo thun Pancake Black");
            item.setQuantity(1);
            item.setUnitPrice(gross);
            item.setTotalPrice(gross);
            item.setRawData("{\"mock\":true}");
            item.setCreatedAt(Instant.now());
            orderItemRepository.save(item);
        }
    }
}
