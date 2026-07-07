export interface RevenueByChannelItem {
  platform: string;
  label: string;
  revenue: number;
  share: number;
  changePercent: number;
}

export interface AdCostBreakdownItem {
  platform: string;
  label: string;
  spend: number;
  share: number;
}

export interface HourlyRevenueItem {
  hour: string;
  todayRevenue: number;
  yesterdayRevenue: number;
}

export interface TopProductItem {
  rank: number;
  productName: string;
  orders: number;
  revenue: number;
}

export interface TopProductByChannelItem {
  rank: number;
  productName: string;
  platform: string;
  orders: number;
  revenue: number;
}

export interface RealtimeOrder {
  id: string;
  createdAt: string;
  orderCode: string;
  customerDisplayName: string;
  platform: 'haravan' | 'shopee' | 'tiktok-shop';
  orderValue: number;
}

export interface TopAdCampaign {
  campaignName: string;
  platform: string;
  spend: number;
  attributedOrders: number;
  roas: number;
}

export interface KpiSparkPoint {
  label: string;
  value: number;
  previousValue?: number;
}

export interface PlatformHealthDetails {
  status: 'ok' | 'warning' | 'error' | 'unknown';
  label: string;
  lastSuccessAt?: string | null;
  lastErrorAt?: string | null;
}

export interface DashboardData {
  revenueToday: number;
  ordersToday: number;
  averageOrderValue: number;
  adSpendToday: number;
  revenueTodayChangePercent: number;
  ordersTodayChangePercent: number;
  averageOrderValueChangePercent: number;
  adSpendTodayChangePercent: number;
  estimatedRoas: number;
  estimatedMer: number;
  cancelRate: number;
  returnOrdersToday: number;

  facebookAdsSpend: number;
  tiktokAdsSpend: number;
  shopeeAdsSpend: number;
  orderStatusCounts?: Record<string, number>;

  kpiSparklines: {
    revenueToday: KpiSparkPoint[];
    ordersToday: KpiSparkPoint[];
    averageOrderValue: KpiSparkPoint[];
    adSpendToday: KpiSparkPoint[];
  };

  revenueByChannel: RevenueByChannelItem[];

  adCostBreakdown: AdCostBreakdownItem[];
  hourlyRevenue: HourlyRevenueItem[];
  hourlyAdCost: {
    hour: string;
    facebookAdsHourlySpend: number;
    tiktokAdsHourlySpend: number;
    shopeeAdsHourlySpend: number;
  }[];

  realtimeOrders: RealtimeOrder[];
  topProducts: TopProductItem[];
  topProductsByChannel: TopProductByChannelItem[];
  topAdCampaigns: TopAdCampaign[];

  platformHealth: {
    pancakePos: PlatformHealthDetails;
    pancakeAds: PlatformHealthDetails;
  };

  lastUpdatedAt: string;
}
