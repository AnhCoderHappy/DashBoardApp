package com.mdata.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PancakeOrderNormalizer {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public NormalizedPancakeOrder normalize(JsonNode orderNode, UUID connectionId) {
        Instant businessTime = firstInstant(orderNode, "inserted_at", "created_at", "updated_at");
        ZonedDateTime businessZoned = businessTime.atZone(BUSINESS_ZONE);
        String status = text(orderNode, "status", "UNKNOWN");
        BigDecimal gross = firstDecimal(orderNode, "total_price", "total_amount", "money_to_collect");
        BigDecimal net = firstDecimal(orderNode, "money_to_collect", "total_price", "total_amount");
        BigDecimal discount = firstDecimal(orderNode, "discount", "discount_amount", "total_discount");
        BigDecimal shipping = firstDecimal(orderNode, "shipping_fee", "shipping_amount");
        Instant updatedAt = firstInstant(orderNode, "updated_at", "last_updated_at", "inserted_at", "created_at");

        List<NormalizedPancakeOrderItem> items = new ArrayList<>();
        JsonNode itemsNode = orderNode.path("items");
        if (itemsNode.isArray()) {
            for (JsonNode itemNode : itemsNode) {
                items.add(normalizeItem(itemNode));
            }
        }

        return new NormalizedPancakeOrder(
                connectionId,
                "pancake",
                extractSourceChannelFromPancake(orderNode),
                text(orderNode, "id", "UNKNOWN_" + System.currentTimeMillis()),
                status,
                MetricsService.normalizeOrderStatus("pancake", status),
                gross,
                net,
                discount,
                shipping,
                text(orderNode, "currency", "VND"),
                customerName(orderNode),
                firstInstant(orderNode, "inserted_at", "created_at"),
                updatedAt,
                businessTime,
                businessZoned.toLocalDate(),
                businessZoned.getHour(),
                text(orderNode, "updated_at", null),
                orderNode.toString(),
                items
        );
    }

    public String extractSourceChannelFromPancake(JsonNode orderNode) {
        String value = firstText(orderNode, "order_sources_name", "source", "source_name", "channel", "shop_channel");
        if (value == null || value.isBlank()) {
            JsonNode page = orderNode.path("page");
            value = firstText(page, "type", "name", "platform");
        }
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase().replace("_", "-");
        if (normalized.contains("shopee")) return "shopee";
        if (normalized.contains("tiktok")) return "tiktok-shop";
        if (normalized.contains("facebook") || normalized.contains("fb")) return "facebook";
        if (normalized.contains("pancake-store") || normalized.contains("pos") || normalized.contains("store")) return "pos";
        return "unknown";
    }

    private NormalizedPancakeOrderItem normalizeItem(JsonNode itemNode) {
        JsonNode variation = itemNode.path("variation_info");
        String productName = firstNonBlank(
                text(variation, "name", null),
                text(itemNode, "product_name", null),
                text(itemNode, "name", "San pham")
        );
        String sku = firstNonBlank(text(variation, "display_id", null), text(variation, "barcode", null), text(itemNode, "sku", ""));
        int quantity = itemNode.path("quantity").asInt(1);
        BigDecimal price = firstDecimal(itemNode, "price", "retail_price");
        if (price.compareTo(BigDecimal.ZERO) == 0) {
            price = decimal(variation, "retail_price");
        }
        BigDecimal discountEach = decimal(itemNode, "discount_each_product");
        BigDecimal netPrice = price.subtract(discountEach);
        return new NormalizedPancakeOrderItem(
                text(itemNode, "product_id", ""),
                text(itemNode, "variation_id", ""),
                sku,
                productName,
                quantity,
                netPrice,
                netPrice.multiply(BigDecimal.valueOf(quantity)),
                itemNode.toString()
        );
    }

    private String customerName(JsonNode orderNode) {
        JsonNode customer = orderNode.path("customer");
        return firstNonBlank(text(customer, "name", null), text(orderNode, "customer_name", "N/A"));
    }

    private Instant firstInstant(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field, null);
            if (value == null || value.isBlank()) continue;
            try {
                if (value.matches("\\d+")) {
                    long epoch = Long.parseLong(value);
                    return epoch > 9_999_999_999L ? Instant.ofEpochMilli(epoch) : Instant.ofEpochSecond(epoch);
                }
                String clean = value.contains(".") && !value.endsWith("Z") ? value.substring(0, value.indexOf(".")) : value;
                if (clean.endsWith("Z") || clean.contains("+")) {
                    return Instant.parse(clean);
                }
                return LocalDateTime.parse(clean, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .atZone(BUSINESS_ZONE)
                        .toInstant();
            } catch (Exception ignored) {
                // ponytail: unsupported vendor date formats fall back to now; add patterns when Pancake sample proves them.
            }
        }
        return Instant.now();
    }

    private BigDecimal firstDecimal(JsonNode node, String... fields) {
        for (String field : fields) {
            BigDecimal value = decimal(node, field);
            if (value.compareTo(BigDecimal.ZERO) != 0) return value;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal decimal(JsonNode node, String field) {
        try {
            JsonNode value = node.path(field);
            if (value.isMissingNode() || value.isNull()) return BigDecimal.ZERO;
            return new BigDecimal(value.asText("0"));
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field, null);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return fallback;
        return value.asText(fallback);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    public record NormalizedPancakeOrder(
            UUID connectionId,
            String platform,
            String sourceChannel,
            String platformOrderId,
            String status,
            String normalizedStatus,
            BigDecimal grossRevenue,
            BigDecimal netRevenue,
            BigDecimal discountAmount,
            BigDecimal shippingFee,
            String currency,
            String customerName,
            Instant createdAtPlatform,
            Instant updatedAtPlatform,
            Instant businessTime,
            LocalDate businessDate,
            Integer businessHour,
            String externalVersion,
            String rawData,
            List<NormalizedPancakeOrderItem> items
    ) {}

    public record NormalizedPancakeOrderItem(
            String platformProductId,
            String platformSkuId,
            String sku,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            String rawData
    ) {}
}
