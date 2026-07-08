package com.mdata.backend.connector;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdata.backend.entity.AdInsightsHourly;
import com.mdata.backend.entity.Order;
import com.mdata.backend.entity.OrderItem;
import com.mdata.backend.repository.OrderItemRepository;
import com.mdata.backend.repository.OrderRepository;
import com.mdata.backend.repository.PlatformConnectionRepository;
import com.mdata.backend.repository.AdInsightsHourlyRepository;
import com.mdata.backend.service.DashboardProjectionService;
import com.mdata.backend.service.MetricsService;
import com.mdata.backend.service.OrderIngestionService;
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
    private final OrderIngestionService orderIngestionService;
    private final DashboardProjectionService dashboardProjectionService;
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
            OrderIngestionService orderIngestionService,
            DashboardProjectionService dashboardProjectionService,
            @Value("${MOCK_PLATFORMS:false}") String mockPlatforms
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.connectionRepository = connectionRepository;
        this.tokenService = tokenService;
        this.adInsightsHourlyRepository = adInsightsHourlyRepository;
        this.orderIngestionService = orderIngestionService;
        this.dashboardProjectionService = dashboardProjectionService;
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
                .timeout(Duration.ofSeconds(30))
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
                .timeout(Duration.ofSeconds(30))
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
                .timeout(Duration.ofSeconds(30))
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
            var result = orderIngestionService.processPancakePayload(connectionId, jsonNode.get("data").toString(), null);
            dashboardProjectionService.updateProjection(result);
        }
    }

    public void processSingleOrderPayload(String payload, UUID connectionId) throws Exception {
        var node = objectMapper.readTree(payload);
        if (node.has("data")) {
            node = node.get("data");
        }
        if (node.isArray()) {
            for (var orderNode : node) {
                var result = orderIngestionService.processPancakeOrder(connectionId, orderNode, null);
                dashboardProjectionService.updateProjection(result);
            }
        } else {
            var result = orderIngestionService.processPancakeOrder(connectionId, node, null);
            dashboardProjectionService.updateProjection(result);
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
            
            BigDecimal gross = BigDecimal.ZERO;
            if (node.has("total_price") && !node.get("total_price").isNull()) gross = new BigDecimal(node.get("total_price").asText());
            else if (node.has("total_amount") && !node.get("total_amount").isNull()) gross = new BigDecimal(node.get("total_amount").asText());
            else if (node.has("money_to_collect") && !node.get("money_to_collect").isNull()) gross = new BigDecimal(node.get("money_to_collect").asText());

            BigDecimal net = BigDecimal.ZERO;
            if (node.has("money_to_collect") && !node.get("money_to_collect").isNull()) net = new BigDecimal(node.get("money_to_collect").asText());
            else if (node.has("total_price") && !node.get("total_price").isNull()) net = new BigDecimal(node.get("total_price").asText());
            else if (node.has("total_amount") && !node.get("total_amount").isNull()) net = new BigDecimal(node.get("total_amount").asText());
                              
            order.setGrossRevenue(gross);
            order.setNetRevenue(net);
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
                .timeout(Duration.ofSeconds(30))
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
            
            AdsAggregation agg = new AdsAggregation();

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
                    agg.campaignIds.add(campaignId);
                    if (!campaignName.isBlank()) {
                        agg.campaignNames.add(campaignName);
                    }

                    String adAccountId = "";
                    if (node.has("ad_account") && !node.get("ad_account").isNull()) {
                        adAccountId = node.get("ad_account").has("id") ? node.get("ad_account").get("id").asText() : "";
                    }
                    if (!adAccountId.isBlank()) {
                        agg.adAccountIds.add(adAccountId);
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

                    if (createdTime.isBefore(agg.earliestCreatedTime)) {
                        agg.earliestCreatedTime = createdTime;
                    }
                    agg.totalSpend = agg.totalSpend.add(spend);
                    agg.totalImpressions += impressions;
                    agg.totalClicks += clicks;
                    agg.totalReach += reach;
                    agg.rawItemCount++;

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

            if (agg.rawItemCount == 0) {
                System.out.println("[Pancake] No ad insights to process for shop " + shopId + ".");
                return;
            }

            saveShopAdsInsight(shopId, agg);
            System.out.println("[Pancake] Processed 1 aggregated ad insight record for shop " + shopId
                    + " from " + agg.campaignIds.size() + " campaigns.");
        }
    }

    private void saveShopAdsInsight(String shopId, AdsAggregation agg) throws Exception {
        boolean hasExistingRecords = adInsightsHourlyRepository.existsByPlatformAndShopId("facebook-ads", shopId);
        Instant hour = (hasExistingRecords ? Instant.now() : agg.earliestCreatedTime).truncatedTo(ChronoUnit.HOURS);

        BigDecimal spend = agg.totalSpend;
        int impressions = agg.totalImpressions;
        int clicks = agg.totalClicks;
        int reach = agg.totalReach;

        if (hasExistingRecords) {
            BigDecimal sumSpend = adInsightsHourlyRepository.sumSpendBeforeHour("facebook-ads", shopId, hour);
            if (sumSpend == null) sumSpend = BigDecimal.ZERO;

            Long sumImpressions = adInsightsHourlyRepository.sumImpressionsBeforeHour("facebook-ads", shopId, hour);
            if (sumImpressions == null) sumImpressions = 0L;

            Long sumClicks = adInsightsHourlyRepository.sumClicksBeforeHour("facebook-ads", shopId, hour);
            if (sumClicks == null) sumClicks = 0L;

            Long sumReach = adInsightsHourlyRepository.sumReachBeforeHour("facebook-ads", shopId, hour);
            if (sumReach == null) sumReach = 0L;

            spend = spend.subtract(sumSpend);
            if (spend.compareTo(BigDecimal.ZERO) < 0) spend = BigDecimal.ZERO;

            impressions -= sumImpressions.intValue();
            if (impressions < 0) impressions = 0;

            clicks -= sumClicks.intValue();
            if (clicks < 0) clicks = 0;

            reach -= sumReach.intValue();
            if (reach < 0) reach = 0;
        }

        AdInsightsHourly values = buildShopAdsInsight(shopId, hour, agg, spend, impressions, clicks, reach);
        adInsightsHourlyRepository.upsertByPlatformShopIdHour(
                UUID.randomUUID(),
                values.getPlatform(),
                values.getShopId(),
                values.getAdAccountId(),
                values.getCampaignId(),
                values.getCampaignName(),
                values.getHour(),
                values.getSpend(),
                values.getImpressions(),
                values.getClicks(),
                values.getReach(),
                values.getCpc(),
                values.getCpm(),
                values.getCtr(),
                values.getRawData(),
                values.getCreatedAt()
        );
    }

    private AdInsightsHourly buildShopAdsInsight(
            String shopId,
            Instant hour,
            AdsAggregation agg,
            BigDecimal spend,
            int impressions,
            int clicks,
            int reach
    ) throws Exception {
        AdInsightsHourly insight = new AdInsightsHourly();
        insight.setPlatform("facebook-ads");
        insight.setShopId(shopId);
        insight.setAdAccountId("all");
        insight.setCampaignId("all");
        insight.setCampaignName("All Campaigns");
        insight.setHour(hour);
        insight.setSpend(spend);
        insight.setImpressions(impressions);
        insight.setClicks(clicks);
        insight.setReach(reach);
        insight.setCpc(clicks > 0 ? spend.divide(BigDecimal.valueOf(clicks), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        insight.setCpm(impressions > 0 ? spend.multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(impressions), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        insight.setCtr(impressions > 0 ? BigDecimal.valueOf(clicks).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(impressions), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        Map<String, Object> rawMap = new LinkedHashMap<>();
        rawMap.put("source", "pancake_ads_v2");
        rawMap.put("campaign_ids", new ArrayList<>(agg.campaignIds));
        rawMap.put("campaign_names", new ArrayList<>(agg.campaignNames));
        rawMap.put("ad_account_ids", new ArrayList<>(agg.adAccountIds));
        rawMap.put("ad_ids", new ArrayList<>(agg.adIds));
        rawMap.put("raw_item_count", agg.rawItemCount);
        insight.setRawData(objectMapper.writeValueAsString(rawMap));
        insight.setCreatedAt(Instant.now());
        return insight;
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

    private static class AdsAggregation {
        Instant earliestCreatedTime = Instant.now();
        BigDecimal totalSpend = BigDecimal.ZERO;
        int totalImpressions = 0;
        int totalClicks = 0;
        int totalReach = 0;
        int rawItemCount = 0;
        Set<String> campaignIds = new HashSet<>();
        Set<String> campaignNames = new HashSet<>();
        Set<String> adAccountIds = new HashSet<>();
        Set<String> adIds = new HashSet<>();
    }

    private void generateMockOrders(UUID connectionId, Instant since) {
        Instant threshold = since != null ? since : Instant.now().minus(24, ChronoUnit.HOURS);
        int count = 5 + (int) (Math.random() * 10);
        
        String[] mockPlatforms = {"shopee", "tiktok-shop", "facebook", "pos"};

        for (int i = 0; i < count; i++) {
            Order order = new Order();
            String platform = mockPlatforms[i % mockPlatforms.length];
            order.setPlatform("pancake");
            order.setSourceChannel(platform);
            order.setPlatformOrderId("PCK-" + System.currentTimeMillis() + "-" + i);
            order.setConnectionId(connectionId);
            
            double r = Math.random();
            String status = r < 0.7 ? "PAID" : r < 0.9 ? "CANCELLED" : "REFUNDED";
            order.setStatus(status);
            order.setNormalizedStatus(MetricsService.normalizeOrderStatus("pancake", status));

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
            order.setBusinessTime(order.getCreatedAtPlatform());
            order.setBusinessDate(order.getCreatedAtPlatform().atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate());
            order.setBusinessHour(order.getCreatedAtPlatform().atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).getHour());
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
