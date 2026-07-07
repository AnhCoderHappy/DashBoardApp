import { useState } from 'react';
import DashboardHeader from './DashboardHeader';
import KpiCard from './KpiCard';
import AdCostDonutCard from './AdCostDonutCard';
import HourlyAdCostLineChart from './HourlyAdCostLineChart';
import MiniMetricGrid from './MiniMetricGrid';
import RealtimeOrdersTable from './RealtimeOrdersTable';
import TopAdCampaignsTable from './TopAdCampaignsTable';
import HourlyRevenueLineChart from './HourlyRevenueLineChart';
import TopProductsTable from './TopProductsTable';
import TopProductsByChannelTable from './TopProductsByChannelTable';
import RevenueShareDonutCard from './RevenueShareDonutCard';
import DataSourceFooter from './DataSourceFooter';
import SettingsView from './SettingsView';
import { DashboardData } from '../../types/dashboard';

interface DashboardPageProps {
  data: DashboardData;
  isRefreshing: boolean;
  onRefresh: () => void;
  error?: string | null;
  selectedShopId: string;
  onShopChange: (shopId: string) => void;
  selectedDate: string;
  onDateChange: (date: string) => void;
}

export default function DashboardPage({
  data,
  isRefreshing,
  onRefresh,
  error,
  selectedShopId,
  onShopChange,
  selectedDate,
  onDateChange
}: DashboardPageProps) {
  const [activeTab, setActiveTab] = useState<'dashboard' | 'settings'>('dashboard');

  // Format selectedDate (YYYY-MM-DD) into DD/MM/YYYY (e.g., 05/07/2026)
  const formatDateLabel = (dateStr: string) => {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length === 3) {
      return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return dateStr;
  };
  const dateLabel = formatDateLabel(selectedDate);

  // Config layout models matching Yêu cầu 5 KPI cards
  const kpiCardsConfig = [
    {
      title: 'DOANH THU ' + dateLabel,
      valueField: 'revenueToday' as const,
      changePercentField: 'revenueTodayChangePercent' as const,
      color: 'green' as const,
      unit: 'đ',
      isCurrency: true,
      compareLabel: 'so với hôm qua',
      sparklineField: 'revenueToday' as const
    },
    {
      title: 'ĐƠN HÀNG ' + dateLabel,
      valueField: 'ordersToday' as const,
      changePercentField: 'ordersTodayChangePercent' as const,
      color: 'blue' as const,
      compareLabel: 'so với hôm qua',
      sparklineField: 'ordersToday' as const
    },
    {
      title: 'GIÁ TRỊ ĐƠN TB',
      valueField: 'averageOrderValue' as const,
      changePercentField: 'averageOrderValueChangePercent' as const,
      color: 'orange' as const,
      unit: 'đ',
      isCurrency: true,
      compareLabel: 'so với hôm qua',
      sparklineField: 'averageOrderValue' as const
    },
    {
      title: 'CHI PHÍ QUẢNG CÁO ' + dateLabel,
      valueField: 'adSpendToday' as const,
      changePercentField: 'adSpendTodayChangePercent' as const,
      color: 'cyan' as const,
      unit: 'đ',
      isCurrency: true,
      compareLabel: 'so với hôm qua',
      sparklineField: 'adSpendToday' as const
    }
  ];

  const donutConfig = {
    title: "CHI PHÍ QUẢNG CÁO",
    subtitle: dateLabel,
    availabilityType: "direct_api",
    valueField: "adSpendToday",
    unit: "đ",
    apiSources: ["Meta Ads Insights API", "TikTok API for Business", "Shopee Marketing API if approved"],
    segments: [
      { label: "Facebook Ads", valueField: "facebookAdsSpend", colorName: "blue" },
      { label: "TikTok Ads", valueField: "tiktokAdsSpend", colorName: "cyan" },
      { label: "Shopee Ads", valueField: "shopeeAdsSpend", colorName: "orange" }
    ]
  };

  const lineChartConfig = {
    title: "CHI PHÍ QUẢNG CÁO THEO GIỜ",
    availabilityType: "partial_api",
    apiSources: [
      "Meta Ads Insights hourly breakdown",
      "TikTok Ads reporting if available",
      "Shopee Ads reporting if approved"
    ],
    series: [
      { label: "Facebook Ads", valueField: "facebookAdsHourlySpend", colorName: "blue" },
      { label: "TikTok Ads", valueField: "tiktokAdsHourlySpend", colorName: "cyan" },
      { label: "Shopee Ads", valueField: "shopeeAdsHourlySpend", colorName: "orange" }
    ],
    note: "Facebook thường lấy được chi tiết hơn. TikTok/Shopee phụ thuộc quyền và độ chi tiết API."
  };

  const miniGridConfig = {
    cards: [
      { cardId: "mini-roas", name: "roas_estimated", title: "ROAS TẠM TÍNH", availabilityType: "requires_tracking", valueField: "estimatedRoas", unit: "x" },
      { cardId: "mini-mer", name: "mer_estimated", title: "MER TẠM TÍNH", availabilityType: "requires_tracking", valueField: "estimatedMer", unit: "x" },
      { cardId: "mini-cancel-rate", name: "cancel_rate", title: "TỶ LỆ HỦY", availabilityType: "partial_api", valueField: "cancelRate", unit: "%" },
      { cardId: "mini-return-orders", name: "return_orders", title: "ĐƠN HOÀN", availabilityType: "partial_api", valueField: "returnOrdersToday", unit: "" }
    ]
  };

  const realtimeOrdersConfig = {
    title: "ĐƠN HÀNG MỚI REALTIME",
    note: "REALTIME",
    availabilityType: "direct_api",
    apiSources: ["Haravan Webhook", "Shopee Push", "TikTok Shop Webhook", "Order APIs for reconciliation"],
    table: {
      columns: [
        { label: "THỜI GIAN", field: "createdAt" },
        { label: "MÃ ĐƠN", field: "orderCode" },
        { label: "KHÁCH HÀNG", field: "customerDisplayName" },
        { label: "KÊNH", field: "platform" },
        { label: "GIÁ TRỊ", field: "orderValue" }
      ]
    },
    logic: "Webhook ghi nhận đơn mới nhanh, polling định kỳ dùng để đối soát nếu webhook bị miss."
  };

  const topAdCampaignsConfig = {
    title: "TOP CHI PHÍ ADS THEO CHIẾN DỊCH",
    subtitle: dateLabel,
    availabilityType: "direct_api",
    apiSources: ["Meta Ads Insights API", "TikTok API for Business", "Shopee Marketing API if approved"],
    table: {
      columns: [
        { label: "CHIẾN DỊCH", field: "campaignName" },
        { label: "NỀN TẢNG", field: "platform" },
        { label: "CHI PHÍ", field: "spend" },
        { label: "ĐƠN HÀNG", field: "attributedOrders" },
        { label: "ROAS", field: "roas" }
      ]
    },
    note: "Chi phí theo campaign lấy được. Đơn hàng và ROAS theo campaign cần tracking/attribution."
  };

  return (
    <main className="dashboard-page mdata-dashboard api-supported-layout dark-theme min-h-screen 2xl:h-screen 2xl:overflow-hidden bg-gradient-to-br from-slate-950 via-[#0a0f1d] to-[#030712] p-4 2xl:p-2 flex flex-col gap-2.5 2xl:gap-1.5 select-none text-slate-100">
      {/* Header bar */}
      <DashboardHeader
        lastUpdatedAt={data.lastUpdatedAt}
        isRefreshing={isRefreshing}
        onRefresh={onRefresh}
        error={error}
        activeTab={activeTab}
        onTabChange={setActiveTab}
        selectedShopId={selectedShopId}
        onShopChange={onShopChange}
        selectedDate={selectedDate}
        onDateChange={onDateChange}
        orderStatusCounts={data.orderStatusCounts}
      />

      {activeTab === 'dashboard' ? (
        <>
          {/* Row 1: KPI Summary Row */}
          <section className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-4 gap-2.5">
            {kpiCardsConfig.map((card) => {
              const val = data[card.valueField as keyof DashboardData] as number;
              const pct = data[card.changePercentField as keyof DashboardData] as number;
              const sparkline = data.kpiSparklines[card.sparklineField];
              const trend = pct > 0 ? 'up' : pct < 0 ? 'down' : 'neutral';
              return (
                <KpiCard
                  key={card.valueField}
                  title={card.title}
                  value={val}
                  unit={card.unit}
                  color={card.color}
                  changePercent={pct}
                  compareLabel={card.compareLabel}
                  trend={trend}
                  sparklineData={sparkline}
                  isCurrency={card.isCurrency}
                />
              );
            })}
          </section>

          {/* Row 2: Sales Channels Share, Ads and Mini Metrics */}
          <section className="grid grid-cols-1 lg:grid-cols-[33%_33%_34%] gap-2.5 2xl:flex-1 2xl:min-h-0">
            <RevenueShareDonutCard
              title="TỶ TRỌNG DOANH THU THEO KÊNH"
              data={data.revenueByChannel}
            />
            <AdCostDonutCard
              title={donutConfig.title}
              subtitle={donutConfig.subtitle}
              value={data.adSpendToday}
              segments={donutConfig.segments}
              data={data as any}
              apiSources={donutConfig.apiSources}
            />
            <MiniMetricGrid
              cards={miniGridConfig.cards}
              data={data as any}
            />
          </section>

          {/* Row 3: Hourly Revenue, Realtime Orders and Hourly Ad Cost */}
          <section className="grid grid-cols-1 lg:grid-cols-[33%_33%_34%] gap-2.5 2xl:flex-1 2xl:min-h-0">
            <HourlyRevenueLineChart
              title="DOANH THU THEO GIỜ"
              data={data.hourlyRevenue}
              apiSources={["Orders hourly aggregation"]}
            />
            <RealtimeOrdersTable
              title={realtimeOrdersConfig.title}
              note={realtimeOrdersConfig.note}
              columns={realtimeOrdersConfig.table.columns}
              data={data.realtimeOrders}
              apiSources={realtimeOrdersConfig.apiSources}
              logic={realtimeOrdersConfig.logic}
            />
            <HourlyAdCostLineChart
              title={lineChartConfig.title}
              series={lineChartConfig.series}
              data={data.hourlyAdCost}
              apiSources={lineChartConfig.apiSources}
              note={lineChartConfig.note}
            />
          </section>

          {/* Row 4: Top Products, Top Products by Channel and Campaign Table */}
          <section className="grid grid-cols-1 lg:grid-cols-[33%_33%_34%] gap-2.5 2xl:flex-1 2xl:min-h-0">
            <TopProductsTable
              title="SẢN PHẨM BÁN CHẠY"
              data={data.topProducts}
            />
            <TopProductsByChannelTable
              title="SẢN PHẨM BÁN CHẠY THEO KÊNH"
              data={data.topProductsByChannel || []}
            />
            <TopAdCampaignsTable
              title={topAdCampaignsConfig.title}
              subtitle={topAdCampaignsConfig.subtitle}
              columns={topAdCampaignsConfig.table.columns}
              data={data.topAdCampaigns}
              apiSources={topAdCampaignsConfig.apiSources}
              note={topAdCampaignsConfig.note}
            />
          </section>

          {/* Footer bar with Data Source Health Statuses */}
          <DataSourceFooter
            platformHealth={data.platformHealth}
            lastUpdatedAt={data.lastUpdatedAt}
            isRefreshing={isRefreshing}
            onRefresh={onRefresh}
          />
        </>
      ) : (
        <SettingsView />
      )}
    </main>
  );
}
