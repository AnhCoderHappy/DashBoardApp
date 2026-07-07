package com.mdata.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class DashboardDataDto {
    private BigDecimal revenueToday;
    private int ordersToday;
    private BigDecimal averageOrderValue;
    private BigDecimal adSpendToday;
    private BigDecimal revenueTodayChangePercent;
    private BigDecimal ordersTodayChangePercent;
    private BigDecimal averageOrderValueChangePercent;
    private BigDecimal adSpendTodayChangePercent;
    private BigDecimal estimatedRoas;
    private BigDecimal estimatedMer;
    private BigDecimal cancelRate;
    private int returnOrdersToday;
    private Map<String, Integer> orderStatusCounts;

    private BigDecimal facebookAdsSpend;
    private BigDecimal tiktokAdsSpend;
    private BigDecimal shopeeAdsSpend;

    private Map<String, List<KpiSparkPointDto>> kpiSparklines;

    private List<RevenueByChannelItemDto> revenueByChannel;

    private List<AdCostBreakdownItemDto> adCostBreakdown;
    private List<HourlyRevenueItemDto> hourlyRevenue;
    private List<HourlyAdCostDto> hourlyAdCost;

    private List<RealtimeOrderDto> realtimeOrders;
    private List<TopProductItemDto> topProducts;
    private List<TopProductByChannelItemDto> topProductsByChannel;
    private List<TopAdCampaignDto> topAdCampaigns;

    private Map<String, PlatformHealthDetailsDto> platformHealth;

    private String lastUpdatedAt;

    @Data
    public static class KpiSparkPointDto {
        private String label;
        private double value;
        private Double previousValue;

        public KpiSparkPointDto() {}
        public KpiSparkPointDto(String label, double value, Double previousValue) {
            this.label = label;
            this.value = value;
            this.previousValue = previousValue;
        }
    }

    @Data
    public static class RevenueByChannelItemDto {
        private String platform;
        private String label;
        private BigDecimal revenue;
        private double share;
        private double changePercent;
    }

    @Data
    public static class AdCostBreakdownItemDto {
        private String platform;
        private String label;
        private BigDecimal spend;
        private double share;
    }

    @Data
    public static class HourlyRevenueItemDto {
        private String hour;
        private BigDecimal todayRevenue;
        private BigDecimal yesterdayRevenue;
    }

    @Data
    public static class HourlyAdCostDto {
        private String hour;
        private BigDecimal facebookAdsHourlySpend;
        private BigDecimal tiktokAdsHourlySpend;
        private BigDecimal shopeeAdsHourlySpend;
    }

    @Data
    public static class RealtimeOrderDto {
        private String id;
        private String createdAt;
        private String orderCode;
        private String customerDisplayName;
        private String platform;
        private BigDecimal orderValue;
    }

    @Data
    public static class TopProductItemDto {
        private int rank;
        private String productName;
        private int orders;
        private BigDecimal revenue;
    }

    @Data
    public static class TopProductByChannelItemDto {
        private int rank;
        private String productName;
        private String platform;
        private int orders;
        private BigDecimal revenue;
    }

    @Data
    public static class TopAdCampaignDto {
        private String campaignName;
        private String platform;
        private BigDecimal spend;
        private int attributedOrders;
        private BigDecimal roas;
    }

    @Data
    public static class PlatformHealthDetailsDto {
        private String status;
        private String label;
        private String lastSuccessAt;
        private String lastErrorAt;
    }
}
