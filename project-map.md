# Project Map & Source Code Snapshot — MData

Generated on: 02:30:41 25/6/2026
This file contains the complete source code tree and contents of all core logic files (libraries and config files are omitted).

## File Structure Tree

```txt
MData/
├── apps/
│   ├── backend/
│   │   └── src/
│   │       ├── config/
│   │       │   └── env.ts
│   │       ├── connectors/
│   │       │   ├── connector.interface.ts
│   │       │   ├── haravan.connector.ts
│   │       │   ├── meta-ads.connector.ts
│   │       │   ├── shopee.connector.ts
│   │       │   ├── tiktok-ads.connector.ts
│   │       │   └── tiktok-shop.connector.ts
│   │       ├── db/
│   │       │   └── supabase.ts
│   │       ├── routes/
│   │       │   ├── dashboard.ts
│   │       │   └── jobs.ts
│   │       ├── services/
│   │       │   ├── alert.service.ts
│   │       │   ├── health.service.ts
│   │       │   ├── metrics.service.ts
│   │       │   └── token.service.ts
│   │       ├── utils/
│   │       │   └── crypto.ts
│   │       └── index.ts
│   ├── dashboard/
│   │   └── src/
│   │       ├── components/
│   │       │   └── dashboard/
│   │       │       ├── AdCostDonutCard.tsx
│   │       │       ├── DashboardHeader.tsx
│   │       │       ├── DashboardPage.tsx
│   │       │       ├── DataSourceFooter.tsx
│   │       │       ├── FullscreenButton.tsx
│   │       │       ├── HourlyAdCostLineChart.tsx
│   │       │       ├── HourlyRevenueLineChart.tsx
│   │       │       ├── KpiCard.tsx
│   │       │       ├── MiniMetricGrid.tsx
│   │       │       ├── PlatformHealthCard.tsx
│   │       │       ├── RealtimeOrdersTable.tsx
│   │       │       ├── RevenueByChannelTable.tsx
│   │       │       ├── RevenueShareDonutCard.tsx
│   │       │       ├── StatusBadge.tsx
│   │       │       ├── TopAdCampaignsTable.tsx
│   │       │       └── TopProductsTable.tsx
│   │       ├── data/
│   │       │   └── mockDashboardData.ts
│   │       ├── types/
│   │       │   └── dashboard.ts
│   │       ├── utils/
│   │       │   ├── formatCurrency.ts
│   │       │   ├── formatNumber.ts
│   │       │   └── time.ts
│   │       ├── App.tsx
│   │       ├── index.css
│   │       ├── main.tsx
│   │       └── vite-env.d.ts
│   └── worker/
│       └── src/
│           └── index.ts
├── packages/
│   └── shared/
│       └── src/
│           ├── constants/
│           │   └── index.ts
│           ├── normalizers/
│           │   └── index.ts
│           ├── types/
│           │   └── index.ts
│           └── index.ts
└── supabase/
    └── migrations/
        └── 20260625000000_init.sql
```

## Source Code Contents

### File: `apps/backend/src/config/env.ts`

```typescript
import dotenv from 'dotenv';
import { z } from 'zod';

dotenv.config();

const envSchema = z.object({
  PORT: z.coerce.number().default(8080),
  NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),
  SUPABASE_URL: z.string().url(),
  SUPABASE_SERVICE_ROLE_KEY: z.string().min(1),
  CRON_SECRET: z.string().min(1),
  ENCRYPTION_KEY: z.string().min(10), // Will be hashed to 32 bytes for AES key
  FRONTEND_ORIGIN: z.string().default('*'),
  BACKEND_URL: z.string().url().default('http://localhost:8080'),
  TELEGRAM_BOT_TOKEN: z.string().optional(),
  TELEGRAM_CHAT_ID: z.string().optional(),
});

const parsed = envSchema.safeParse(process.env);

if (!parsed.success) {
  console.error('❌ Invalid environment variables:', JSON.stringify(parsed.error.format(), null, 2));
  process.exit(1);
}

export const env = parsed.data;
```

---

### File: `apps/backend/src/connectors/connector.interface.ts`

```typescript
import { PlatformName } from 'shared';

export interface SyncResult {
  platform: PlatformName;
  jobName: string;
  status: 'success' | 'failed';
  recordsProcessed: number;
  errorMessage?: string;
  metadata?: any;
}

export interface PlatformConnector {
  platform: PlatformName;
  refreshToken(connectionId: string): Promise<void>;
  syncOrders(connectionId: string, since?: Date): Promise<SyncResult>;
  syncAdsInsights?(connectionId: string, since?: Date): Promise<SyncResult>;
}
```

---

### File: `apps/backend/src/connectors/haravan.connector.ts`

```typescript
import { PlatformConnector, SyncResult } from './connector.interface';
import { supabase } from '../db/supabase';
import { getConnectionToken } from '../services/token.service';
import { normalizeOrderStatus } from 'shared';

export class HaravanConnector implements PlatformConnector {
  platform = 'haravan' as const;

  async refreshToken(connectionId: string): Promise<void> {
    // Haravan long-lived access tokens usually do not require refresh, or OAuth 2.0 flow is handled externally.
    // If needed, we implement refresh logic here.
    console.log(`[Haravan] Token refresh requested for connection ${connectionId} (noop).`);
  }

  async syncOrders(connectionId: string, since?: Date): Promise<SyncResult> {
    const jobName = 'haravan_orders_sync';
    const startedAt = new Date().toISOString();
    let recordsProcessed = 0;

    try {
      // 1. Get credentials
      const { accessToken } = await getConnectionToken(connectionId);
      const isMock = accessToken === 'mock-token' || process.env.MOCK_PLATFORMS === 'true';

      let rawOrders: any[] = [];

      if (isMock) {
        // Generate mock Haravan orders for today
        rawOrders = this.generateMockOrders(since);
      } else {
        // Perform real API call to Haravan
        const sinceStr = since ? since.toISOString() : new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
        const response = await fetch(
          `https://haravan-api.com/admin/orders.json?created_at_min=${sinceStr}&limit=50`,
          {
            headers: {
              'Authorization': `Bearer ${accessToken}`,
              'Content-Type': 'application/json',
            },
          }
        );

        if (!response.ok) {
          throw new Error(`Haravan API returned HTTP ${response.status}: ${await response.text()}`);
        }
        const data = await response.json();
        rawOrders = data.orders || [];
      }

      // 2. Process and Normalize orders
      for (const raw of rawOrders) {
        const platformOrderId = String(raw.id);
        const status = raw.financial_status || 'pending';
        const normalizedStatus = normalizeOrderStatus('haravan', status);

        const grossRevenue = Number(raw.total_price || 0);
        const discountAmount = Number(raw.total_discounts || 0);
        const shippingFee = Number(raw.total_shipping_price || 0);
        // Net revenue = Gross revenue - Discount - Shipping (or gross, depending on definition. Let's do Gross - Discounts)
        const netRevenue = grossRevenue - discountAmount;

        // Upsert order
        const { data: orderRow, error: orderErr } = await supabase
          .from('orders')
          .upsert({
            platform: this.platform,
            platform_order_id: platformOrderId,
            connection_id: connectionId,
            status,
            normalized_status: normalizedStatus,
            gross_revenue: grossRevenue,
            net_revenue: netRevenue,
            discount_amount: discountAmount,
            shipping_fee: shippingFee,
            currency: raw.currency || 'VND',
            customer_name: raw.customer ? `${raw.customer.first_name || ''} ${raw.customer.last_name || ''}`.trim() : 'N/A',
            created_at_platform: raw.created_at,
            updated_at_platform: raw.updated_at,
            raw_data: raw,
            updated_at: new Date().toISOString()
          }, {
            onConflict: 'platform,platform_order_id'
          })
          .select('id')
          .single();

        if (orderErr) throw orderErr;

        // Upsert order items
        if (raw.line_items && orderRow) {
          // Delete old order items first for idempotency
          await supabase.from('order_items').delete().eq('order_id', orderRow.id);

          for (const item of raw.line_items) {
            const { error: itemErr } = await supabase.from('order_items').insert({
              order_id: orderRow.id,
              platform_product_id: String(item.product_id),
              platform_sku_id: String(item.variant_id),
              sku: item.sku,
              product_name: item.title,
              quantity: Number(item.quantity || 0),
              unit_price: Number(item.price || 0),
              total_price: Number(item.price || 0) * Number(item.quantity || 0),
              raw_data: item,
            });
            if (itemErr) throw itemErr;
          }
        }
        recordsProcessed++;
      }

      // Log success in sync_logs
      await supabase.from('sync_logs').insert({
        platform: this.platform,
        job_name: jobName,
        status: 'success',
        started_at: startedAt,
        finished_at: new Date().toISOString(),
        records_processed: recordsProcessed,
      });

      // Update platform health
      await supabase.from('platform_health').upsert({
        platform: this.platform,
        status: 'ok',
        last_success_at: new Date().toISOString(),
        latency_ms: 120,
        updated_at: new Date().toISOString()
      });

      return {
        platform: this.platform,
        jobName,
        status: 'success',
        recordsProcessed,
      };
    } catch (error: any) {
      console.error(`[Haravan Sync Error]:`, error);

      // Log failure in sync_logs
      await supabase.from('sync_logs').insert({
        platform: this.platform,
        job_name: jobName,
        status: 'failed',
        started_at: startedAt,
        finished_at: new Date().toISOString(),
        records_processed: 0,
        error_message: error.message,
      });

      // Update platform health to error
      await supabase.from('platform_health').upsert({
        platform: this.platform,
        status: 'error',
        last_error_at: new Date().toISOString(),
        last_error_message: error.message,
        updated_at: new Date().toISOString()
      });

      return {
        platform: this.platform,
        jobName,
        status: 'failed',
        recordsProcessed: 0,
        errorMessage: error.message,
      };
    }
  }

  private generateMockOrders(since?: Date): any[] {
    const orders: any[] = [];
    const count = Math.floor(Math.random() * 5) + 2; // Generate 2-6 orders
    const now = new Date();

    for (let i = 0; i < count; i++) {
      const orderTime = new Date(now.getTime() - Math.random() * 6 * 60 * 60 * 1000); // within last 6 hours
      const price1 = 120000;
      const qty1 = Math.floor(Math.random() * 2) + 1;
      const orderId = `HRV-${Date.now()}-${i}`;

      orders.push({
        id: orderId,
        financial_status: 'paid',
        total_price: price1 * qty1,
        total_discounts: 10000 * i,
        total_shipping_price: 30000,
        currency: 'VND',
        created_at: orderTime.toISOString(),
        updated_at: orderTime.toISOString(),
        customer: {
          first_name: 'Nguyễn',
          last_name: `Văn Haravan ${i}`,
        },
        line_items: [
          {
            product_id: 1001 + i,
            variant_id: 2001 + i,
            sku: `HRV-SHIRT-${i}`,
            title: `Haravan Premium Polo Shirt ${i}`,
            quantity: qty1,
            price: price1,
          },
        ],
      });
    }
    return orders;
  }
}
```

---

### File: `apps/backend/src/connectors/meta-ads.connector.ts`

```typescript
import { PlatformConnector, SyncResult } from './connector.interface';
import { supabase } from '../db/supabase';
import { getConnectionToken } from '../services/token.service';

export class MetaAdsConnector implements PlatformConnector {
  platform = 'meta-ads' as const;

  async refreshToken(connectionId: string): Promise<void> {
    console.log(`[Meta Ads] Token refresh requested for connection ${connectionId} (noop).`);
  }

  async syncOrders(connectionId: string, since?: Date): Promise<SyncResult> {
    // Meta Ads does not have orders, but we must implement the interface
    return {
      platform: this.platform,
      jobName: 'meta_ads_orders_sync_noop',
      status: 'success',
      recordsProcessed: 0,
    };
  }

  async syncAdsInsights(connectionId: string, since?: Date): Promise<SyncResult> {
    const jobName = 'meta_ads_insights_sync';
    const startedAt = new Date().toISOString();
    let recordsProcessed = 0;

    try {
      // 1. Get credentials and meta
      const { data: connection } = await supabase
        .from('platform_connections')
        .select('shop_id') // We store the ad_account_id in shop_id field
        .eq('id', connectionId)
        .single();

      const adAccountId = connection?.shop_id || 'act_mock_account';
      const { accessToken } = await getConnectionToken(connectionId);
      const isMock = accessToken === 'mock-token' || process.env.MOCK_PLATFORMS === 'true';

      let rawInsights: any[] = [];

      if (isMock) {
        rawInsights = this.generateMockInsights(since);
      } else {
        // Real Meta Ads API call
        // Fetch hourly ad account campaign metrics
        const sinceStr = since ? since.toISOString().split('T')[0] : new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString().split('T')[0];
        const fields = 'campaign_id,campaign_name,spend,impressions,clicks,reach,conversions';
        const url = `https://graph.facebook.com/v18.0/${adAccountId}/insights?fields=${fields}&time_range={'since':'${sinceStr}','until':'${new Date().toISOString().split('T')[0]}'}&time_increment=hourly_by_advertiser_time&access_token=${accessToken}`;

        const response = await fetch(url);
        if (!response.ok) {
          throw new Error(`Meta Ads API returned HTTP ${response.status}: ${await response.text()}`);
        }

        const data = await response.json();
        rawInsights = data.data || [];
      }

      // 2. Process and Save hourly insights
      for (const raw of rawInsights) {
        const campaignId = raw.campaign_id;
        const campaignName = raw.campaign_name || 'Unnamed Campaign';
        const hourStr = raw.hour || raw.date_start; // Format ISO string for the specific hour start

        const spend = Number(raw.spend || 0);
        const impressions = Number(raw.impressions || 0);
        const clicks = Number(raw.clicks || 0);
        const reach = Number(raw.reach || 0);
        const conversions = Number(raw.conversions || 0);

        const cpc = clicks > 0 ? spend / clicks : 0;
        const cpm = impressions > 0 ? (spend / impressions) * 1000 : 0;
        const ctr = impressions > 0 ? clicks / impressions : 0;

        const { error: upsertErr } = await supabase
          .from('ad_insights_hourly')
          .upsert({
            platform: this.platform,
            ad_account_id: adAccountId,
            campaign_id: campaignId,
            campaign_name: campaignName,
            hour: hourStr,
            spend,
            impressions,
            clicks,
            reach,
            cpc,
            cpm,
            ctr,
            conversions,
            raw_data: raw,
            created_at: new Date().toISOString()
          }, {
            onConflict: 'platform,ad_account_id,campaign_id,hour'
          });

        if (upsertErr) throw upsertErr;
        recordsProcessed++;
      }

      // Log success
      await supabase.from('sync_logs').insert({
        platform: this.platform,
        job_name: jobName,
        status: 'success',
        started_at: startedAt,
        finished_at: new Date().toISOString(),
        records_processed: recordsProcessed,
      });

      // Update platform health
      await supabase.from('platform_health').upsert({
        platform: this.platform,
        status: 'ok',
        last_success_at: new Date().toISOString(),
        latency_ms: 220,
        updated_at: new Date().toISOString()
      });

      return {
        platform: this.platform,
        jobName,
        status: 'success',
        recordsProcessed,
      };
    } catch (error: any) {
      console.error(`[Meta Ads Sync Error]:`, error);

      await supabase.from('sync_logs').insert({
        platform: this.platform,
        job_name: jobName,
        status: 'failed',
        started_at: startedAt,
        finished_at: new Date().toISOString(),
        records_processed: 0,
        error_message: error.message,
      });

      await supabase.from('platform_health').upsert({
        platform: this.platform,
        status: 'error',
        last_error_at: new Date().toISOString(),
        last_error_message: error.message,
        updated_at: new Date().toISOString()
      });

      return {
        platform: this.platform,
        jobName,
        status: 'failed',
        recordsProcessed: 0,
        errorMessage: error.message,
      };
    }
  }

  private generateMockInsights(since?: Date): any[] {
    const insights: any[] = [];
    const now = new Date();
    const startHour = since ? new Date(since) : new Date(Date.now() - 12 * 60 * 60 * 1000);
    startHour.setMinutes(0, 0, 0);

    const campaigns = [
      { id: 'camp-meta-1', name: 'Meta Ads Conversions Apparel' },
      { id: 'camp-meta-2', name: 'Meta Ads Retargeting Customers' }
    ];

    // Generate insights for each hour from startHour to now
    let currentHour = new Date(startHour);
    while (currentHour.getTime() <= now.getTime()) {
      for (const camp of campaigns) {
        const hourStr = currentHour.toISOString();
        const randBase = Math.random();

        // Let's create varying spend per hour
        const spend = Math.floor(20000 + randBase * 30000); // 20k to 50k VND per hour
        const impressions = Math.floor(1000 + randBase * 1500);
        const clicks = Math.floor(impressions * (0.015 + randBase * 0.02)); // 1.5% to 3.5% CTR
        const reach = Math.floor(impressions * 0.8);
        const conversions = Math.floor(clicks * (0.05 + randBase * 0.1)); // 5% to 15% CVR

        insights.push({
          campaign_id: camp.id,
          campaign_name: camp.name,
          hour: hourStr,
          spend,
          impressions,
          clicks,
          reach,
          conversions,
        });
      }
      currentHour = new Date(currentHour.getTime() + 60 * 60 * 1000); // next hour
    }

    return insights;
  }
}
```

---

### File: `apps/backend/src/connectors/shopee.connector.ts`

```typescript
import crypto from 'crypto';
import { PlatformConnector, SyncResult } from './connector.interface';
import { supabase } from '../db/supabase';
import { getConnectionToken, updateConnectionToken } from '../services/token.service';
import { normalizeOrderStatus } from 'shared';

export class ShopeeConnector implements PlatformConnector {
  platform = 'shopee' as const;

  private partnerId = process.env.SHOPEE_PARTNER_ID || '123456';
  private partnerKey = process.env.SHOPEE_PARTNER_KEY || 'mock-partner-key';
  private apiBase = process.env.SHOPEE_API_BASE || 'https://partner.shopeemobile.com';

  private buildUrl(path: string, accessToken?: string, shopId?: string): string {
    const timestamp = Math.floor(Date.now() / 1000);
    let signBase = '';

    if (accessToken && shopId) {
      signBase = `${this.partnerId}${path}${timestamp}${accessToken}${shopId}`;
    } else {
      signBase = `${this.partnerId}${path}${timestamp}`;
    }

    const sign = crypto
      .createHmac('sha256', this.partnerKey)
      .update(signBase)
      .digest('hex');

    const url = new URL(`${this.apiBase}${path}`);
    url.searchParams.append('partner_id', this.partnerId);
    url.searchParams.append('timestamp', String(timestamp));
    url.searchParams.append('sign', sign);
    if (accessToken) url.searchParams.append('access_token', accessToken);
    if (shopId) url.searchParams.append('shop_id', shopId);

    return url.toString();
  }

  async refreshToken(connectionId: string): Promise<void> {
    console.log(`[Shopee] Token refresh requested for connection ${connectionId}.`);
    const { refreshToken } = await getConnectionToken(connectionId);
    if (!refreshToken || refreshToken === 'mock-refresh-token' || process.env.MOCK_PLATFORMS === 'true') {
      console.log(`[Shopee] Using mock refresh token (noop).`);
      return;
    }

    const path = '/api/v2/public/refresh_access_token';
    const url = this.buildUrl(path);

    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        refresh_token: refreshToken,
        partner_id: Number(this.partnerId),
      }),
    });

    if (!response.ok) {
      throw new Error(`Shopee refresh token API returned HTTP ${response.status}: ${await response.text()}`);
    }

    const data = await response.json();
    if (data.error) {
      throw new Error(`Shopee token refresh error: ${data.message || data.error}`);
    }

    const nextAccessToken = data.access_token;
    const nextRefreshToken = data.refresh_token;
    const expiresAt = new Date(Date.now() + Number(data.expire_in || 14400) * 1000);

    await updateConnectionToken(connectionId, nextAccessToken, nextRefreshToken, expiresAt);
  }

  async syncOrders(connectionId: string, since?: Date): Promise<SyncResult> {
    const jobName = 'shopee_orders_sync';
    const startedAt = new Date().toISOString();
    let recordsProcessed = 0;

    try {
      // 1. Get credentials and connection meta
      const { data: connection } = await supabase
        .from('platform_connections')
        .select('shop_id')
        .eq('id', connectionId)
        .single();

      const shopId = connection?.shop_id || 'mock-shop';
      const { accessToken } = await getConnectionToken(connectionId);
      const isMock = accessToken === 'mock-token' || process.env.MOCK_PLATFORMS === 'true';

      let rawOrders: any[] = [];

      if (isMock) {
        rawOrders = this.generateMockOrders(since);
      } else {
        // Real Shopee API order fetch
        const sinceTimestamp = since ? Math.floor(since.getTime() / 1000) : Math.floor((Date.now() - 24 * 60 * 60 * 1000) / 1000);
        const listPath = '/api/v2/order/get_order_list';
        const listUrl = this.buildUrl(listPath, accessToken, shopId) + `&time_range_field=create_time&time_from=${sinceTimestamp}&time_to=${Math.floor(Date.now() / 1000)}&page_size=20`;

        const listRes = await fetch(listUrl);
        if (!listRes.ok) throw new Error(`Shopee get_order_list returned HTTP ${listRes.status}`);
        const listData = await listRes.json();
        if (listData.error) throw new Error(`Shopee list error: ${listData.message || listData.error}`);

        const orderIds = (listData.response?.order_list || []).map((o: any) => o.order_sn);

        if (orderIds.length > 0) {
          const detailPath = '/api/v2/order/get_order_detail';
          const detailUrl = this.buildUrl(detailPath, accessToken, shopId) + `&order_sn_list=${orderIds.join(',')}&response_optional_fields=item_list,buyer_username`;

          const detailRes = await fetch(detailUrl);
          if (!detailRes.ok) throw new Error(`Shopee get_order_detail returned HTTP ${detailRes.status}`);
          const detailData = await detailRes.json();
          if (detailData.error) throw new Error(`Shopee details error: ${detailData.message || detailData.error}`);

          rawOrders = detailData.response?.order_list || [];
        }
      }

      // 2. Process and Normalize Shopee Orders
      for (const raw of rawOrders) {
        const platformOrderId = raw.order_sn;
        const status = raw.order_status || 'UNPAID';
        const normalizedStatus = normalizeOrderStatus('shopee', status);

        // Shopee prices are typically floats (gross_revenue is total_amount)
        const grossRevenue = Number(raw.total_amount || 0);
        const discountAmount = Number(raw.seller_discount || 0);
        const shippingFee = Number(raw.actual_shipping_fee || 0);
        const netRevenue = grossRevenue - discountAmount;

        // Upsert order
        const { data: orderRow, error: orderErr } = await supabase
          .from('orders')
          .upsert({
            platform: this.platform,
            platform_order_id: platformOrderId,
            connection_id: connectionId,
            status,
            normalized_status: normalizedStatus,
            gross_revenue: grossRevenue,
            net_revenue: netRevenue,
            discount_amount: discountAmount,
            shipping_fee: shippingFee,
            currency: raw.currency || 'VND',
            customer_name: raw.buyer_username || 'Shopee Customer',
            created_at_platform: new Date(Number(raw.create_time) * 1000).toISOString(),
            updated_at_platform: new Date(Number(raw.update_time || raw.create_time) * 1000).toISOString(),
            raw_data: raw,
            updated_at: new Date().toISOString()
          }, {
            onConflict: 'platform,platform_order_id'
          })
          .select('id')
          .single();

        if (orderErr) throw orderErr;

        // Upsert order items
        if (raw.item_list && orderRow) {
          await supabase.from('order_items').delete().eq('order_id', orderRow.id);

          for (const item of raw.item_list) {
            const qty = Number(item.model_quantity_purchased || item.quantity_purchased || 0);
            const price = Number(item.model_original_price || item.original_price || 0);

            await supabase.from('order_items').insert({
              order_id: orderRow.id,
              platform_product_id: String(item.item_id),
              platform_sku_id: String(item.model_id),
              sku: item.model_sku || item.item_sku,
              product_name: item.item_name,
              quantity: qty,
              unit_price: price,
              total_price: price * qty,
              raw_data: item,
            });
          }
        }
        recordsProcessed++;
      }

      // Log success
      await supabase.from('sync_logs').insert({
        platform: this.platform,
        job_name: jobName,
        status: 'success',
        started_at: startedAt,
        finished_at: new Date().toISOString(),
        records_processed: recordsProcessed,
      });

      // Update platform health
      await supabase.from('platform_health').upsert({
        platform: this.platform,
        status: 'ok',
        last_success_at: new Date().toISOString(),
        latency_ms: 180,
        updated_at: new Date().toISOString()
      });

      return {
        platform: this.platform,
        jobName,
        status: 'success',
        recordsProcessed,
      };
    } catch (error: any) {
      console.error(`[Shopee Sync Error]:`, error);

      await supabase.from('sync_logs').insert({
        platform: this.platform,
        job_name: jobName,
        status: 'failed',
        started_at: startedAt,
        finished_at: new Date().toISOString(),
        records_processed: 0,
        error_message: error.message,
      });

      await supabase.from('platform_health').upsert({
        platform: this.platform,
        status: 'error',
        last_error_at: new Date().toISOString(),
        last_error_message: error.message,
        updated_at: new Date().toISOString()
      });

      return {
        platform: this.platform,
        jobName,
        status: 'failed',
        recordsProcessed: 0,
        errorMessage: error.message,
      };
    }
  }

  private generateMockOrders(since?: Date): any[] {
    const orders: any[] = [];
    const count = Math.floor(Math.random() * 5) + 3; // Generate 3-7 orders
    const now = new Date();

    for (let i = 0; i < count; i++) {
      const orderTime = new Date(now.getTime() - Math.random() * 10 * 60 * 60 * 1000);
      const price1 = 150000;
      const qty1 = Math.floor(Math.random() * 3) + 1;
      const orderSn = `SHP-${Date.now()}-${i}`;

      orders.push({
        order_sn: orderSn,
        order_status: 'COMPLETED',
        total_amount: price1 * qty1,
        seller_discount: 15000 * i,
        actual_shipping_fee: 35000,
        currency: 'VND',
        create_time: Math.floor(orderTime.getTime() / 1000),
        update_time: Math.floor(orderTime.getTime() / 1000),
        buyer_username: `shopee_user_${i}`,
        item_list: [
          {
            item_id: 20001 + i,
            model_id: 30001 + i,
            item_sku: `SHP-SKU-${i}`,
            item_name: `Shopee Trend Sweater ${i}`,
            model_quantity_purchased: qty1,
            model_original_price: price1,
          },
        ],
      });
    }
    return orders;
  }
}
```

---

### File: `apps/backend/src/connectors/tiktok-ads.connector.ts`

```typescript
import { PlatformConnector, SyncResult } from './connector.interface';
import { supabase } from '../db/supabase';
import { getConnectionToken } from '../services/token.service';

export class TikTokAdsConnector implements PlatformConnector {
  platform = 'tiktok-ads' as const;

  async refreshToken(connectionId: string): Promise<void> {
    console.log(`[TikTok Ads] Token refresh requested for connection ${connectionId} (noop).`);
  }

  async syncOrders(connectionId: string, since?: Date): Promise<SyncResult> {
    return {
      platform: this.platform,
      jobName: 'tiktok_ads_orders_sync_noop',
      status: 'success',
      recordsProcessed: 0,
    };
  }

  async syncAdsInsights(connectionId: string, since?: Date): Promise<SyncResult> {
    const jobName = 'tiktok_ads_insights_sync';
    const startedAt = new Date().toISOString();
    let recordsProcessed = 0;

    try {
      const { data: connection } = await supabase
        .from('platform_connections')
        .select('shop_id') // We store the advertiser_id in shop_id field
        .eq('id', connectionId)
        .single();

      const advertiserId = connection?.shop_id || 'mock_advertiser_id';
      const { accessToken } = await getConnectionToken(connectionId);
      const isMock = accessToken === 'mock-token' || process.env.MOCK_PLATFORMS === 'true';

      let rawInsights: any[] = [];

      if (isMock) {
        rawInsights = this.generateMockInsights(since);
      } else {
        // Real TikTok Ads API call
        const sinceStr = since ? since.toISOString().split('T')[0] : new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString().split('T')[0];
        const url = `https://business-api.tiktok.com/open_api/v1.3/report/integrated/get/?advertiser_id=${advertiserId}&report_type=BASIC&data_level=AUCTION_CAMPAIGN&dimensions=["campaign_id","hourly"]&metrics=["spend","impressions","clicks","conversions"]&start_date=${sinceStr}&end_date=${new Date().toISOString().split('T')[0]}`;

        const response = await fetch(url, {
          headers: {
            'Access-Token': accessToken,
            'Content-Type': 'application/json',
          },
        });

        if (!response.ok) {
          throw new Error(`TikTok Ads API returned HTTP ${response.status}: ${await response.text()}`);
        }

        const data = await response.json();
        if (data.code !== 0) {
          throw new Error(`TikTok Ads API returned error code ${data.code}: ${data.message}`);
        }
        rawInsights = data.data?.list || [];
      }

      // 2. Process and Save hourly insights
      for (const raw of rawInsights) {
        const campaignId = raw.campaign_id || raw.dimensions?.campaign_id;
        const campaignName = raw.campaign_name || raw.metrics?.campaign_name || 'Unnamed TikTok Campaign';
        
        // TikTok API hour is usually given inside raw.dimensions.hourly or raw.hourly
        const hourStr = raw.hour || raw.dimensions?.hourly || new Date().toISOString(); 

        const spend = Number(raw.spend || raw.metrics?.spend || 0);
        const impressions = Number(raw.impressions || raw.metrics?.impressions || 0);
        const clicks = Number(raw.clicks || raw.metrics?.clicks || 0);
        const reach = Number(raw.reach || raw.metrics?.reach || impressions * 0.7); // TikTok doesn't always provide reach hourly, so approximate
        const conversions = Number(raw.conversions || raw.metrics?.conversions || 0);

        const cpc = clicks > 0 ? spend / clicks : 0;
        const cpm = impressions > 0 ? (spend / impressions) * 1000 : 0;
        const ctr = impressions > 0 ? clicks / impressions : 0;

        const { error: upsertErr } = await supabase
          .from('ad_insights_hourly')
          .upsert({
            platform: this.platform,
            ad_account_id: advertiserId,
            campaign_id: campaignId,
            campaign_name: campaignName,
            hour: hourStr,
            spend,
            impressions,
            clicks,
            reach,
            cpc,
            cpm,
            ctr,
            conversions,
            raw_data: raw,
            created_at: new Date().toISOString()
          }, {
            onConflict: 'platform,ad_account_id,campaign_id,hour'
          });

        if (upsertErr) throw upsertErr;
        recordsProcessed++;
      }

      // Log success
      await supabase.from('sync_logs').insert({
        platform: this.platform,
        job_name: jobName,
        status: 'success',
        started_at: startedAt,
        finished_at: new Date().toISOString(),
        records_processed: recordsProcessed,
      });

      // Update platform health
      await supabase.from('platform_health').upsert({
        platform: this.platform,
        status: 'ok',
        last_success_at: new Date().toISOString(),
        latency_ms: 190,
        updated_at: new Date().toISOString()
      });

      return {
        platform: this.platform,
        jobName,
        status: 'success',
        recordsProcessed,
      };
    } catch (error: any) {
      console.error(`[TikTok Ads Sync Error]:`, error);

      await supabase.from('sync_logs').insert({
        platform: this.platform,
        job_name: jobName,
        status: 'failed',
        started_at: startedAt,
        finished_at: new Date().toISOString(),
        records_processed: 0,
        error_message: error.message,
      });

      await supabase.from('platform_health').upsert({
        platform: this.platform,
        status: 'error',
        last_error_at: new Date().toISOString(),
        last_error_message: error.message,
        updated_at: new Date().toISOString()
      });

      return {
        platform: this.platform,
        jobName,
        status: 'failed',
        recordsProcessed: 0,
        errorMessage: error.message,
      };
    }
  }

  private generateMockInsights(since?: Date): any[] {
    const insights: any[] = [];
    const now = new Date();
    const startHour = since ? new Date(since) : new Date(Date.now() - 12 * 60 * 60 * 1000);
    startHour.setMinutes(0, 0, 0);

    const campaigns = [
      { id: 'camp-tt-1', name: 'TikTok Ads Conversions - Video Hook 1' },
      { id: 'camp-tt-2', name: 'TikTok Ads Spark Ads - Influencer B' }
    ];

    let currentHour = new Date(startHour);
    while (currentHour.getTime() <= now.getTime()) {
      for (const camp of campaigns) {
        const hourStr = currentHour.toISOString();
        const randBase = Math.random();

        const spend = Math.floor(15000 + randBase * 25000); // 15k to 40k VND per hour
        const impressions = Math.floor(1200 + randBase * 2000);
        const clicks = Math.floor(impressions * (0.02 + randBase * 0.03)); // 2% to 5% CTR
        const conversions = Math.floor(clicks * (0.04 + randBase * 0.08));

        insights.push({
          campaign_id: camp.id,
          campaign_name: camp.name,
          hour: hourStr,
          spend,
          impressions,
          clicks,
          conversions,
        });
      }
      currentHour = new Date(currentHour.getTime() + 60 * 60 * 1000);
    }

    return insights;
  }
}
```

---

### File: `apps/backend/src/connectors/tiktok-shop.connector.ts`

```typescript
import { PlatformConnector, SyncResult } from './connector.interface';
import { supabase } from '../db/supabase';
import { getConnectionToken, updateConnectionToken } from '../services/token.service';
import { normalizeOrderStatus } from 'shared';

export class TikTokShopConnector implements PlatformConnector {
  platform = 'tiktok-shop' as const;

  private appKey = process.env.TTS_APP_KEY || 'mock-app-key';
  private appSecret = process.env.TTS_APP_SECRET || 'mock-app-secret';
  private apiBase = process.env.TTS_API_BASE || 'https://open-api.tiktokglobalshop.com';

  async refreshToken(connectionId: string): Promise<void> {
    console.log(`[TikTok Shop] Token refresh requested for connection ${connectionId}.`);
    const { refreshToken } = await getConnectionToken(connectionId);
    if (!refreshToken || refreshToken === 'mock-refresh-token' || process.env.MOCK_PLATFORMS === 'true') {
      console.log(`[TikTok Shop] Using mock refresh token (noop).`);
      return;
    }

    const response = await fetch(`${this.apiBase}/api/v2/token/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        app_key: this.appKey,
        app_secret: this.appSecret,
        refresh_token: refreshToken,
        grant_type: 'refresh_token',
      }),
    });

    if (!response.ok) {
      throw new Error(`TikTok Shop refresh token returned HTTP ${response.status}: ${await response.text()}`);
    }

    const data = await response.json();
    if (data.code !== 0) {
      throw new Error(`TikTok Shop token refresh error: ${data.message || data.code}`);
    }

    const nextAccessToken = data.data.access_token;
    const nextRefreshToken = data.data.refresh_token;
    const expiresAt = new Date(Date.now() + Number(data.data.access_token_expire_in || 86400) * 1000);

    await updateConnectionToken(connectionId, nextAccessToken, nextRefreshToken, expiresAt);
  }

  async syncOrders(connectionId: string, since?: Date): Promise<SyncResult> {
    const jobName = 'tiktok_shop_orders_sync';
    const startedAt = new Date().toISOString();
    let recordsProcessed = 0;

    try {
      const { accessToken } = await getConnectionToken(connectionId);
      const isMock = accessToken === 'mock-token' || process.env.MOCK_PLATFORMS === 'true';

      let rawOrders: any[] = [];

      if (isMock) {
        rawOrders = this.generateMockOrders(since);
      } else {
        // Real API Call to TikTok Shop Orders Search API
        const sinceTimestamp = since ? Math.floor(since.getTime() / 1000) : Math.floor((Date.now() - 24 * 60 * 60 * 1000) / 1000);
        
        const searchUrl = `${this.apiBase}/api/v2/order/orders/search?app_key=${this.appKey}&access_token=${accessToken}`;
        const searchRes = await fetch(searchUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            create_time_from: sinceTimestamp,
            create_time_to: Math.floor(Date.now() / 1000),
            page_size: 20,
          }),
        });

        if (!searchRes.ok) throw new Error(`TikTok Shop search_orders returned HTTP ${searchRes.status}`);
        const searchData = await searchRes.json();
        if (searchData.code !== 0) throw new Error(`TTS Search error: ${searchData.message}`);

        const orderIds = (searchData.data?.orders || []).map((o: any) => o.order_id);

        if (orderIds.length > 0) {
          const detailUrl = `${this.apiBase}/api/v2/order/orders/detail?app_key=${this.appKey}&access_token=${accessToken}`;
          const detailRes = await fetch(detailUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ order_ids: orderIds }),
          });

          if (!detailRes.ok) throw new Error(`TikTok Shop order_detail returned HTTP ${detailRes.status}`);
          const detailData = await detailRes.json();
          if (detailData.code !== 0) throw new Error(`TTS Details error: ${detailData.message}`);

          rawOrders = detailData.data?.order_list || [];
        }
      }

      // 2. Process and Normalize TikTok Shop Orders
      for (const raw of rawOrders) {
        const platformOrderId = raw.id || raw.order_id;
        const status = raw.status || 'UNPAID';
        const normalizedStatus = normalizeOrderStatus('tiktok-shop', status);

        const paymentInfo = raw.payment_info || {};
        const grossRevenue = Number(paymentInfo.total_amount || 0);
        const discountAmount = Number(paymentInfo.discount_amount || 0);
        const shippingFee = Number(paymentInfo.shipping_fee || 0);
        const netRevenue = grossRevenue - discountAmount;

        // Upsert order
        const { data: orderRow, error: orderErr } = await supabase
          .from('orders')
          .upsert({
            platform: this.platform,
            platform_order_id: platformOrderId,
            connection_id: connectionId,
            status,
            normalized_status: normalizedStatus,
            gross_revenue: grossRevenue,
            net_revenue: netRevenue,
            discount_amount: discountAmount,
            shipping_fee: shippingFee,
            currency: raw.currency || 'VND',
            customer_name: raw.buyer_email || 'TikTok Shop Customer',
            created_at_platform: new Date(Number(raw.create_time || Date.now() / 1000) * 1000).toISOString(),
            updated_at_platform: new Date(Number(raw.update_time || Date.now() / 1000) * 1000).toISOString(),
            raw_data: raw,
            updated_at: new Date().toISOString()
          }, {
            onConflict: 'platform,platform_order_id'
          })
          .select('id')
          .single();

        if (orderErr) throw orderErr;

        // Upsert order items
        if (raw.line_items && orderRow) {
          await supabase.from('order_items').delete().eq('order_id', orderRow.id);

          for (const item of raw.line_items) {
            const qty = Number(item.quantity || 0);
            const price = Number(item.price || 0);

            await supabase.from('order_items').insert({
              order_id: orderRow.id,
              platform_product_id: String(item.product_id),
              platform_sku_id: String(item.sku_id),
              sku: item.sku,
              product_name: item.product_name,
              quantity: qty,
              unit_price: price,
              total_price: price * qty,
              raw_data: item,
            });
          }
        }
        recordsProcessed++;
      }

      // Log success
      await supabase.from('sync_logs').insert({
        platform: this.platform,
        job_name: jobName,
        status: 'success',
        started_at: startedAt,
        finished_at: new Date().toISOString(),
        records_processed: recordsProcessed,
      });

      // Update platform health
      await supabase.from('platform_health').upsert({
        platform: this.platform,
        status: 'ok',
        last_success_at: new Date().toISOString(),
        latency_ms: 150,
        updated_at: new Date().toISOString()
      });

      return {
        platform: this.platform,
        jobName,
        status: 'success',
        recordsProcessed,
      };
    } catch (error: any) {
      console.error(`[TikTok Shop Sync Error]:`, error);

      await supabase.from('sync_logs').insert({
        platform: this.platform,
        job_name: jobName,
        status: 'failed',
        started_at: startedAt,
        finished_at: new Date().toISOString(),
        records_processed: 0,
        error_message: error.message,
      });

      await supabase.from('platform_health').upsert({
        platform: this.platform,
        status: 'error',
        last_error_at: new Date().toISOString(),
        last_error_message: error.message,
        updated_at: new Date().toISOString()
      });

      return {
        platform: this.platform,
        jobName,
        status: 'failed',
        recordsProcessed: 0,
        errorMessage: error.message,
      };
    }
  }

  private generateMockOrders(since?: Date): any[] {
    const orders: any[] = [];
    const count = Math.floor(Math.random() * 4) + 1; // Generate 1-5 orders
    const now = new Date();

    for (let i = 0; i < count; i++) {
      const orderTime = new Date(now.getTime() - Math.random() * 8 * 60 * 60 * 1000);
      const price1 = 180000;
      const qty1 = Math.floor(Math.random() * 2) + 1;
      const orderId = `TTS-${Date.now()}-${i}`;

      orders.push({
        order_id: orderId,
        status: 'COMPLETED',
        payment_info: {
          total_amount: price1 * qty1,
          discount_amount: 20000 * i,
          shipping_fee: 40000,
        },
        currency: 'VND',
        create_time: Math.floor(orderTime.getTime() / 1000),
        update_time: Math.floor(orderTime.getTime() / 1000),
        buyer_email: `tiktok_user_${i}@example.com`,
        line_items: [
          {
            product_id: 40001 + i,
            sku_id: 50001 + i,
            sku: `TTS-HOODIE-${i}`,
            product_name: `TikTok Shop Viral Hoodie ${i}`,
            quantity: qty1,
            price: price1,
          },
        ],
      });
    }
    return orders;
  }
}
```

---

### File: `apps/backend/src/db/supabase.ts`

```typescript
import { createClient } from '@supabase/supabase-js';
import { env } from '../config/env';

export const supabase = createClient(env.SUPABASE_URL, env.SUPABASE_SERVICE_ROLE_KEY, {
  auth: {
    persistSession: false,
    autoRefreshToken: false,
  },
});
```

---

### File: `apps/backend/src/index.ts`

```typescript
import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import { env } from './config/env';
import { checkHealth } from './services/health.service';
import { sendAlert } from './services/alert.service';
import jobsRouter from './routes/jobs';
import dashboardRouter from './routes/dashboard';

const app = express();

// Enable CORS with settings
app.use(cors({
  origin: env.FRONTEND_ORIGIN === '*' ? '*' : env.FRONTEND_ORIGIN.split(','),
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'x-cron-secret'],
}));

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Request log middleware
app.use((req: Request, res: Response, next: NextFunction) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
  next();
});

// GET /health - Lightweight ping for Render wake and status check
app.get('/health', async (req: Request, res: Response) => {
  try {
    const report = await checkHealth();
    res.status(report.ok ? 200 : 500).json(report);
  } catch (err: any) {
    res.status(500).json({
      ok: false,
      service: 'mdata-backend',
      timestamp: new Date().toISOString(),
      uptime: process.uptime(),
      database: 'error',
      details: err.message,
    });
  }
});

// Mount routes
app.use('/jobs', jobsRouter);
app.use('/api/dashboard', dashboardRouter);

// Centralized error handler
app.use((err: any, req: Request, res: Response, next: NextFunction) => {
  console.error('Unhandled Server Error:', err);

  // Dispatch alert to Telegram
  sendAlert('Server Route Exception', `Path: ${req.method} ${req.path}\nError: ${err.message || String(err)}`)
    .catch((alertErr) => console.error('Alert failure:', alertErr));

  res.status(err.status || 500).json({
    error: true,
    message: err.message || 'Internal Server Error',
    timestamp: new Date().toISOString(),
  });
});

// Start listening
const server = app.listen(env.PORT, () => {
  console.log(`🚀 MData Backend Service started on port ${env.PORT} in ${env.NODE_ENV} mode`);
});

// Graceful shutdown
process.on('SIGTERM', () => {
  console.log('SIGTERM signal received. Closing HTTP server...');
  server.close(() => {
    console.log('HTTP server closed.');
    process.exit(0);
  });
});
```

---

### File: `apps/backend/src/routes/dashboard.ts`

```typescript
import { Router, Request, Response, NextFunction } from 'express';
import { getLiveDashboardData } from '../services/metrics.service';

const router = Router();

// GET /api/dashboard/live
router.get('/live', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const data = await getLiveDashboardData();
    res.json(data);
  } catch (error: any) {
    next(error);
  }
});

export default router;
```

---

### File: `apps/backend/src/routes/jobs.ts`

```typescript
import { Router, Request, Response, NextFunction } from 'express';
import { env } from '../config/env';
import { supabase } from '../db/supabase';
import { HaravanConnector } from '../connectors/haravan.connector';
import { ShopeeConnector } from '../connectors/shopee.connector';
import { TikTokShopConnector } from '../connectors/tiktok-shop.connector';
import { MetaAdsConnector } from '../connectors/meta-ads.connector';
import { TikTokAdsConnector } from '../connectors/tiktok-ads.connector';
import { rebuildHourlyMetrics, rebuildDailyMetrics } from '../services/metrics.service';
import { sendAlert } from '../services/alert.service';
import { normalizeOrderStatus } from 'shared';

const router = Router();

// Middleware to verify CRON_SECRET
function requireCronSecret(req: Request, res: Response, next: NextFunction) {
  const secret = req.headers['x-cron-secret'] || req.query['secret'];
  if (!secret || secret !== env.CRON_SECRET) {
    res.status(401).json({ error: 'Unauthorized: Invalid cron secret' });
    return;
  }
  next();
}

// Apply authentication middleware to all /jobs/* endpoints
router.use(requireCronSecret);

// Instantiate platform connectors
const haravan = new HaravanConnector();
const shopee = new ShopeeConnector();
const tiktokShop = new TikTokShopConnector();
const metaAds = new MetaAdsConnector();
const tiktokAds = new TikTokAdsConnector();

// Helper to get connection ID for a platform
async function getPlatformConnectionId(platform: string): Promise<string> {
  const { data, error } = await supabase
    .from('platform_connections')
    .select('id')
    .eq('platform', platform)
    .eq('status', 'active')
    .limit(1)
    .maybeSingle();

  if (error || !data) {
    // If no active connection, look for any connection or throw
    const { data: fallback } = await supabase
      .from('platform_connections')
      .select('id')
      .eq('platform', platform)
      .limit(1)
      .maybeSingle();

    if (!fallback) {
      throw new Error(`No platform connection found in Supabase for ${platform}`);
    }
    return fallback.id;
  }

  return data.id;
}

// POST /jobs/ping
router.post('/ping', (req: Request, res: Response) => {
  res.json({ ok: true, message: 'Pong! Job endpoint is accessible.', timestamp: new Date().toISOString() });
});

// POST /jobs/sync/haravan/orders
router.post('/sync/haravan/orders', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const connectionId = await getPlatformConnectionId('haravan');
    const result = await haravan.syncOrders(connectionId);
    res.json(result);
  } catch (error: any) {
    next(error);
  }
});

// POST /jobs/sync/shopee/orders
router.post('/sync/shopee/orders', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const connectionId = await getPlatformConnectionId('shopee');
    // Refresh token if needed
    try {
      await shopee.refreshToken(connectionId);
    } catch (refreshErr: any) {
      await sendAlert('Shopee Token Refresh Failed', refreshErr.message);
    }
    const result = await shopee.syncOrders(connectionId);
    res.json(result);
  } catch (error: any) {
    next(error);
  }
});

// POST /jobs/sync/tiktok-shop/orders
router.post('/sync/tiktok-shop/orders', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const connectionId = await getPlatformConnectionId('tiktok-shop');
    try {
      await tiktokShop.refreshToken(connectionId);
    } catch (refreshErr: any) {
      await sendAlert('TikTok Shop Token Refresh Failed', refreshErr.message);
    }
    const result = await tiktokShop.syncOrders(connectionId);
    res.json(result);
  } catch (error: any) {
    next(error);
  }
});

// POST /jobs/sync/meta-ads
router.post('/sync/meta-ads', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const connectionId = await getPlatformConnectionId('meta-ads');
    const result = await metaAds.syncAdsInsights(connectionId);
    res.json(result);
  } catch (error: any) {
    next(error);
  }
});

// POST /jobs/sync/tiktok-ads
router.post('/jobs/sync/tiktok-ads', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const connectionId = await getPlatformConnectionId('tiktok-ads');
    const result = await tiktokAds.syncAdsInsights(connectionId);
    res.json(result);
  } catch (error: any) {
    next(error);
  }
});

// POST /jobs/process-webhook-events
router.post('/process-webhook-events', async (req: Request, res: Response, next: NextFunction) => {
  try {
    // 1. Fetch pending webhook events
    const { data: events, error } = await supabase
      .from('webhook_events')
      .select('*')
      .eq('status', 'pending')
      .order('received_at', { ascending: true })
      .limit(50);

    if (error) throw error;

    let processedCount = 0;

    for (const event of (events || [])) {
      try {
        // Mark as processing
        await supabase.from('webhook_events').update({ status: 'processing' }).eq('id', event.id);

        const payload = event.payload;

        if (event.platform === 'haravan') {
          // Haravan order webhook payload is standard order JSON
          const orderId = String(payload.id);
          const status = payload.financial_status || 'pending';
          const normalizedStatus = normalizeOrderStatus('haravan', status);

          const gross = Number(payload.total_price || 0);
          const discount = Number(payload.total_discounts || 0);
          const shipping = Number(payload.total_shipping_price || 0);
          const net = gross - discount;

          const { data: orderRow, error: orderErr } = await supabase
            .from('orders')
            .upsert({
              platform: 'haravan',
              platform_order_id: orderId,
              status,
              normalized_status: normalizedStatus,
              gross_revenue: gross,
              net_revenue: net,
              discount_amount: discount,
              shipping_fee: shipping,
              currency: payload.currency || 'VND',
              customer_name: payload.customer ? `${payload.customer.first_name || ''} ${payload.customer.last_name || ''}`.trim() : 'N/A',
              created_at_platform: payload.created_at,
              updated_at_platform: payload.updated_at || payload.created_at,
              raw_data: payload,
              updated_at: new Date().toISOString()
            }, {
              onConflict: 'platform,platform_order_id'
            })
            .select('id')
            .single();

          if (orderErr) throw orderErr;

          if (payload.line_items && orderRow) {
            await supabase.from('order_items').delete().eq('order_id', orderRow.id);
            for (const item of payload.line_items) {
              await supabase.from('order_items').insert({
                order_id: orderRow.id,
                platform_product_id: String(item.product_id),
                platform_sku_id: String(item.variant_id),
                sku: item.sku,
                product_name: item.title,
                quantity: Number(item.quantity || 0),
                unit_price: Number(item.price || 0),
                total_price: Number(item.price || 0) * Number(item.quantity || 0),
                raw_data: item,
              });
            }
          }
        } else if (event.platform === 'shopee') {
          // Shopee order webhook: contains order_sn and status
          const orderSn = payload.data?.order_sn || payload.order_sn;
          const status = payload.data?.status || payload.status || 'UNPAID';
          const normalizedStatus = normalizeOrderStatus('shopee', status);

          // Webhook might not contain full line item details.
          // To be simple and idempotent, update order status if order exists, or insert raw placeholder.
          // In real production, we would trigger an immediate queue to fetch order details.
          // Let's do an upsert with existing data fallback:
          const { data: existing } = await supabase
            .from('orders')
            .select('*')
            .eq('platform', 'shopee')
            .eq('platform_order_id', orderSn)
            .maybeSingle();

          await supabase.from('orders').upsert({
            platform: 'shopee',
            platform_order_id: orderSn,
            status,
            normalized_status: normalizedStatus,
            gross_revenue: existing ? existing.gross_revenue : Number(payload.data?.total_amount || 0),
            net_revenue: existing ? existing.net_revenue : Number(payload.data?.total_amount || 0),
            currency: 'VND',
            created_at_platform: existing ? existing.created_at_platform : new Date().toISOString(),
            updated_at_platform: new Date().toISOString(),
            raw_data: payload,
            updated_at: new Date().toISOString()
          }, {
            onConflict: 'platform,platform_order_id'
          });
        } else if (event.platform === 'tiktok-shop') {
          // TikTok Shop webhook
          const orderId = payload.data?.order_id || payload.order_id;
          const status = payload.data?.order_status || payload.order_status || 'UNPAID';
          const normalizedStatus = normalizeOrderStatus('tiktok-shop', status);

          const { data: existing } = await supabase
            .from('orders')
            .select('*')
            .eq('platform', 'tiktok-shop')
            .eq('platform_order_id', orderId)
            .maybeSingle();

          await supabase.from('orders').upsert({
            platform: 'tiktok-shop',
            platform_order_id: orderId,
            status,
            normalized_status: normalizedStatus,
            gross_revenue: existing ? existing.gross_revenue : 0,
            net_revenue: existing ? existing.net_revenue : 0,
            currency: 'VND',
            created_at_platform: existing ? existing.created_at_platform : new Date().toISOString(),
            updated_at_platform: new Date().toISOString(),
            raw_data: payload,
            updated_at: new Date().toISOString()
          }, {
            onConflict: 'platform,platform_order_id'
          });
        }

        // Mark as processed
        await supabase
          .from('webhook_events')
          .update({
            status: 'processed',
            processed_at: new Date().toISOString(),
            error_message: null
          })
          .eq('id', event.id);

        processedCount++;
      } catch (err: any) {
        console.error(`Error processing webhook event ${event.id}:`, err);
        await supabase
          .from('webhook_events')
          .update({
            status: 'failed',
            error_message: err.message,
            processed_at: new Date().toISOString()
          })
          .eq('id', event.id);
      }
    }

    res.json({ success: true, processed: processedCount });
  } catch (error: any) {
    next(error);
  }
});

// POST /jobs/rebuild-summary
router.post('/rebuild-summary', async (req: Request, res: Response, next: NextFunction) => {
  try {
    // Rebuild hourly metrics for last 48 hours
    await rebuildHourlyMetrics(48);
    // Rebuild daily metrics for last 7 days
    await rebuildDailyMetrics(7);

    res.json({ success: true, message: 'Summary tables (hourly_metrics & daily_metrics) rebuilt successfully.' });
  } catch (error: any) {
    next(error);
  }
});

// POST /jobs/cleanup
router.post('/cleanup', async (req: Request, res: Response, next: NextFunction) => {
  try {
    const startedAt = new Date().toISOString();
    let deletedWebhooks = 0;
    let deletedLogs = 0;

    // 1. Delete webhook events older than 14 days
    const webhookThreshold = new Date(Date.now() - 14 * 24 * 60 * 60 * 1000).toISOString();
    const { count: webhooksCount, error: webhooksErr } = await supabase
      .from('webhook_events')
      .delete({ count: 'exact' })
      .lt('received_at', webhookThreshold);

    if (webhooksErr) throw webhooksErr;
    deletedWebhooks = webhooksCount || 0;

    // 2. Delete sync logs older than 30 days
    const logsThreshold = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
    const { count: logsCount, error: logsErr } = await supabase
      .from('sync_logs')
      .delete({ count: 'exact' })
      .lt('started_at', logsThreshold);

    if (logsErr) throw logsErr;
    deletedLogs = logsCount || 0;

    // Log the cleanup job execution
    await supabase.from('sync_logs').insert({
      platform: 'system',
      job_name: 'database_cleanup',
      status: 'success',
      started_at: startedAt,
      finished_at: new Date().toISOString(),
      records_processed: deletedWebhooks + deletedLogs,
      metadata: { deletedWebhooks, deletedLogs }
    });

    res.json({
      success: true,
      deletedWebhooks,
      deletedLogs,
      message: 'Cleanup job completed. Standard data retention policies applied.'
    });
  } catch (error: any) {
    next(error);
  }
});

export default router;
```

---

### File: `apps/backend/src/services/alert.service.ts`

```typescript
import { env } from '../config/env';
import { supabase } from '../db/supabase';

// In-memory cache for throttling: key -> last alert timestamp
const alertCache = new Map<string, number>();
const THROTTLE_WINDOW_MS = 30 * 60 * 1000; // 30 minutes

export async function sendAlert(title: string, message: string, force: boolean = false): Promise<void> {
  const cacheKey = `${title}:${message.substring(0, 50)}`;
  const now = Date.now();
  const lastAlertTime = alertCache.get(cacheKey);

  if (!force && lastAlertTime && now - lastAlertTime < THROTTLE_WINDOW_MS) {
    console.log(`[Alert Throttled] ${title}: ${message}`);
    return;
  }

  // Record alert timestamp
  alertCache.set(cacheKey, now);

  const fullMessage = `⚠️ [MData Alert]\n*${title}*\n\n${message}\n\nTimestamp: ${new Date().toISOString()}`;

  // 1. Log to console
  console.error(`🚨 ALERT: ${title} - ${message}`);

  // 2. Log alert events to database (using sync_logs with status 'alert')
  try {
    await supabase.from('sync_logs').insert({
      platform: 'system',
      job_name: 'system_alert',
      status: 'failed',
      error_message: `${title}: ${message}`,
      metadata: { type: 'alert', title, message }
    });
  } catch (dbErr) {
    console.error('Failed to log alert to database:', dbErr);
  }

  // 3. Send to Telegram if configured
  if (env.TELEGRAM_BOT_TOKEN && env.TELEGRAM_CHAT_ID) {
    try {
      const url = `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`;
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          chat_id: env.TELEGRAM_CHAT_ID,
          text: fullMessage,
          parse_mode: 'Markdown',
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        console.error(`Telegram API error: ${response.status} - ${errorText}`);
      }
    } catch (telegramErr) {
      console.error('Failed to send Telegram alert:', telegramErr);
    }
  }
}
```

---

### File: `apps/backend/src/services/health.service.ts`

```typescript
import { supabase } from '../db/supabase';

export interface HealthReport {
  ok: boolean;
  service: string;
  timestamp: string;
  uptime: number;
  database: 'ok' | 'error';
  details?: string;
}

export async function checkHealth(): Promise<HealthReport> {
  let databaseStatus: 'ok' | 'error' = 'ok';
  let details = '';

  try {
    // Perform a lightweight query on Supabase (e.g. counting connections)
    const { error } = await supabase.from('platform_connections').select('count', { count: 'exact', head: true });
    if (error) {
      databaseStatus = 'error';
      details = error.message;
    }
  } catch (err: any) {
    databaseStatus = 'error';
    details = err.message;
  }

  return {
    ok: databaseStatus === 'ok',
    service: 'mdata-backend',
    timestamp: new Date().toISOString(),
    uptime: process.uptime(),
    database: databaseStatus,
    ...(details ? { details } : {}),
  };
}
```

---

### File: `apps/backend/src/services/metrics.service.ts`

```typescript
import { supabase } from '../db/supabase';
import { LiveDashboardData, PlatformHealth } from 'shared';

// Helper to truncate date to hour
function getStartOfHour(date: Date): Date {
  const d = new Date(dateStrUTC(date));
  d.setMinutes(0, 0, 0);
  return d;
}

// Convert Date to ISO string in UTC or formatted YYYY-MM-DD
function dateStrUTC(d: Date): string {
  return d.toISOString();
}

function getLocalDateString(d: Date): string {
  // Get date in YYYY-MM-DD for local time (Vietnam UTC+7)
  const offset = 7 * 60; // in minutes
  const localTime = new Date(d.getTime() + offset * 60 * 1000);
  return localTime.toISOString().split('T')[0];
}

export async function rebuildHourlyMetrics(sinceHours: number = 48): Promise<void> {
  const since = new Date(Date.now() - sinceHours * 60 * 60 * 1000);
  since.setMinutes(0, 0, 0);

  // 1. Fetch orders since threshold
  const { data: orders, error: ordersErr } = await supabase
    .from('orders')
    .select('platform, status, normalized_status, gross_revenue, net_revenue, created_at_platform')
    .gte('created_at_platform', since.toISOString());

  if (ordersErr) throw ordersErr;

  // 2. Fetch ad insights since threshold
  const { data: adInsights, error: adErr } = await supabase
    .from('ad_insights_hourly')
    .select('platform, spend, hour')
    .gte('hour', since.toISOString());

  if (adErr) throw adErr;

  // 3. Map to aggregate map: platform_hour -> values
  const aggregates: Record<string, {
    platform: string;
    hour: string;
    order_count: number;
    gross_revenue: number;
    net_revenue: number;
    cancelled_count: number;
    refund_count: number;
    ad_spend: number;
  }> = {};

  // Initialize helper to get aggregate key
  const getAggKey = (platform: string, hourStr: string) => `${platform}_${hourStr}`;

  // Process orders
  orders?.forEach((order) => {
    const pDate = new Date(order.created_at_platform);
    pDate.setMinutes(0, 0, 0);
    const hourStr = pDate.toISOString();
    const key = getAggKey(order.platform, hourStr);

    if (!aggregates[key]) {
      aggregates[key] = {
        platform: order.platform,
        hour: hourStr,
        order_count: 0,
        gross_revenue: 0,
        net_revenue: 0,
        cancelled_count: 0,
        refund_count: 0,
        ad_spend: 0,
      };
    }

    const agg = aggregates[key];
    if (order.normalized_status === 'completed') {
      agg.order_count += 1;
      agg.gross_revenue += Number(order.gross_revenue || 0);
      agg.net_revenue += Number(order.net_revenue || 0);
    } else if (order.normalized_status === 'cancelled') {
      agg.cancelled_count += 1;
    } else if (order.normalized_status === 'refunded') {
      agg.refund_count += 1;
    } else {
      // Pending/Processing count towards gross but not completed orders
      agg.gross_revenue += Number(order.gross_revenue || 0);
    }
  });

  // Process ad spend
  adInsights?.forEach((ad) => {
    const adHour = new Date(ad.hour);
    adHour.setMinutes(0, 0, 0);
    const hourStr = adHour.toISOString();
    const key = getAggKey(ad.platform, hourStr);

    if (!aggregates[key]) {
      aggregates[key] = {
        platform: ad.platform,
        hour: hourStr,
        order_count: 0,
        gross_revenue: 0,
        net_revenue: 0,
        cancelled_count: 0,
        refund_count: 0,
        ad_spend: 0,
      };
    }

    aggregates[key].ad_spend += Number(ad.spend || 0);
  });

  // 4. Upsert into database
  for (const key of Object.keys(aggregates)) {
    const agg = aggregates[key];
    const roas = agg.ad_spend > 0 ? agg.net_revenue / agg.ad_spend : 0;

    const { error: upsertErr } = await supabase
      .from('hourly_metrics')
      .upsert({
        platform: agg.platform,
        hour: agg.hour,
        order_count: agg.order_count,
        gross_revenue: agg.gross_revenue,
        net_revenue: agg.net_revenue,
        cancelled_count: agg.cancelled_count,
        refund_count: agg.refund_count,
        ad_spend: agg.ad_spend,
        roas,
        updated_at: new Date().toISOString()
      }, {
        onConflict: 'platform,hour'
      });

    if (upsertErr) {
      console.error(`Failed to upsert hourly metrics for ${key}:`, upsertErr);
    }
  }
}

export async function rebuildDailyMetrics(sinceDays: number = 7): Promise<void> {
  const since = new Date(Date.now() - sinceDays * 24 * 60 * 60 * 1000);
  since.setHours(0, 0, 0, 0);

  // 1. Fetch orders since threshold
  const { data: orders, error: ordersErr } = await supabase
    .from('orders')
    .select('platform, status, normalized_status, gross_revenue, net_revenue, created_at_platform')
    .gte('created_at_platform', since.toISOString());

  if (ordersErr) throw ordersErr;

  // 2. Fetch ad insights since threshold
  const { data: adInsights, error: adErr } = await supabase
    .from('ad_insights_hourly')
    .select('platform, spend, hour')
    .gte('hour', since.toISOString());

  if (adErr) throw adErr;

  // 3. Aggregate by platform and date (in Vietnam GMT+7 timezone for office display)
  const aggregates: Record<string, {
    platform: string;
    date: string;
    order_count: number;
    gross_revenue: number;
    net_revenue: number;
    cancelled_count: number;
    refund_count: number;
    ad_spend: number;
  }> = {};

  const getAggKey = (platform: string, dateStr: string) => `${platform}_${dateStr}`;

  // Process orders
  orders?.forEach((order) => {
    const orderDate = new Date(order.created_at_platform);
    const dateStr = getLocalDateString(orderDate);
    const key = getAggKey(order.platform, dateStr);

    if (!aggregates[key]) {
      aggregates[key] = {
        platform: order.platform,
        date: dateStr,
        order_count: 0,
        gross_revenue: 0,
        net_revenue: 0,
        cancelled_count: 0,
        refund_count: 0,
        ad_spend: 0,
      };
    }

    const agg = aggregates[key];
    if (order.normalized_status === 'completed') {
      agg.order_count += 1;
      agg.gross_revenue += Number(order.gross_revenue || 0);
      agg.net_revenue += Number(order.net_revenue || 0);
    } else if (order.normalized_status === 'cancelled') {
      agg.cancelled_count += 1;
    } else if (order.normalized_status === 'refunded') {
      agg.refund_count += 1;
    } else {
      agg.gross_revenue += Number(order.gross_revenue || 0);
    }
  });

  // Process ad spend
  adInsights?.forEach((ad) => {
    const adHour = new Date(ad.hour);
    const dateStr = getLocalDateString(adHour);
    const key = getAggKey(ad.platform, dateStr);

    if (!aggregates[key]) {
      aggregates[key] = {
        platform: ad.platform,
        date: dateStr,
        order_count: 0,
        gross_revenue: 0,
        net_revenue: 0,
        cancelled_count: 0,
        refund_count: 0,
        ad_spend: 0,
      };
    }

    aggregates[key].ad_spend += Number(ad.spend || 0);
  });

  // 4. Upsert into database
  for (const key of Object.keys(aggregates)) {
    const agg = aggregates[key];
    const roas = agg.ad_spend > 0 ? agg.net_revenue / agg.ad_spend : 0;

    const { error: upsertErr } = await supabase
      .from('daily_metrics')
      .upsert({
        platform: agg.platform,
        date: agg.date,
        order_count: agg.order_count,
        gross_revenue: agg.gross_revenue,
        net_revenue: agg.net_revenue,
        cancelled_count: agg.cancelled_count,
        refund_count: agg.refund_count,
        ad_spend: agg.ad_spend,
        roas,
        updated_at: new Date().toISOString()
      }, {
        onConflict: 'platform,date'
      });

    if (upsertErr) {
      console.error(`Failed to upsert daily metrics for ${key}:`, upsertErr);
    }
  }
}

export async function getLiveDashboardData(): Promise<LiveDashboardData> {
  const todayLocal = getLocalDateString(new Date());

  // 1. Fetch platform health
  const { data: healthData, error: healthErr } = await supabase
    .from('platform_health')
    .select('*');

  const healthMap: Record<string, PlatformHealth> = {};
  healthData?.forEach((h) => {
    healthMap[h.platform] = {
      platform: h.platform,
      status: h.status as any,
      last_success_at: h.last_success_at,
      last_error_at: h.last_error_at,
      last_error_message: h.last_error_message,
      latency_ms: h.latency_ms,
      updated_at: h.updated_at,
    };
  });

  // 2. Fetch today's daily metrics (aggregated by rebuild service)
  const { data: dailyData, error: dailyErr } = await supabase
    .from('daily_metrics')
    .select('*')
    .eq('date', todayLocal);

  if (dailyErr) throw dailyErr;

  let totalRevenueToday = 0;
  let totalOrdersToday = 0;
  let adSpendToday = 0;

  const platformBreakdown: Record<string, { revenue: number; orders: number; spend: number; roas: number }> = {
    haravan: { revenue: 0, orders: 0, spend: 0, roas: 0 },
    shopee: { revenue: 0, orders: 0, spend: 0, roas: 0 },
    'tiktok-shop': { revenue: 0, orders: 0, spend: 0, roas: 0 },
    'meta-ads': { revenue: 0, orders: 0, spend: 0, roas: 0 },
    'tiktok-ads': { revenue: 0, orders: 0, spend: 0, roas: 0 },
  };

  dailyData?.forEach((row) => {
    const platform = row.platform;
    const revenue = Number(row.net_revenue || 0);
    const orders = Number(row.order_count || 0);
    const spend = Number(row.ad_spend || 0);

    totalRevenueToday += revenue;
    totalOrdersToday += orders;
    adSpendToday += spend;

    if (platformBreakdown[platform]) {
      platformBreakdown[platform] = {
        revenue,
        orders,
        spend,
        roas: spend > 0 ? revenue / spend : 0,
      };
    } else {
      // Just in case
      platformBreakdown[platform] = {
        revenue,
        orders,
        spend,
        roas: spend > 0 ? revenue / spend : 0,
      };
    }
  });

  const roasToday = adSpendToday > 0 ? totalRevenueToday / adSpendToday : 0;

  // 3. Fetch hourly trends for today (local time)
  // Retrieve hourly metrics matching today's local date
  const offset = 7 * 60; // in minutes (GMT+7)
  const localMidnightUTC = new Date(new Date().setHours(0, 0, 0, 0) - offset * 60 * 1000);

  const { data: hourlyData, error: hourlyErr } = await supabase
    .from('hourly_metrics')
    .select('*')
    .gte('hour', localMidnightUTC.toISOString())
    .order('hour', { ascending: true });

  if (hourlyErr) throw hourlyErr;

  const hourlyTrendsMap = new Map<string, { revenue: number; orders: number }>();
  // Initialize 24 hours
  for (let i = 0; i < 24; i++) {
    const hrStr = String(i).padStart(2, '0') + ':00';
    hourlyTrendsMap.set(hrStr, { revenue: 0, orders: 0 });
  }

  hourlyData?.forEach((row) => {
    const hourDate = new Date(row.hour);
    // Convert to GMT+7 hour
    const localHour = new Date(hourDate.getTime() + offset * 60 * 1000).getHours();
    const hrStr = String(localHour).padStart(2, '0') + ':00';
    const existing = hourlyTrendsMap.get(hrStr) || { revenue: 0, orders: 0 };
    existing.revenue += Number(row.net_revenue || 0);
    existing.orders += Number(row.order_count || 0);
    hourlyTrendsMap.set(hrStr, existing);
  });

  const hourlyTrends = Array.from(hourlyTrendsMap.entries()).map(([hour, val]) => ({
    hour,
    revenue: val.revenue,
    orders: val.orders,
  }));

  // 4. Fetch Top Products sold today
  // Query raw order_items and orders from today to list top sold products
  const { data: topProductsData, error: topProdErr } = await supabase
    .from('order_items')
    .select(`
      quantity,
      total_price,
      sku,
      product_name,
      orders!inner (
        created_at_platform,
        normalized_status
      )
    `)
    .gte('orders.created_at_platform', localMidnightUTC.toISOString())
    .eq('orders.normalized_status', 'completed');

  const productsMap = new Map<string, { sku: string; product_name: string; quantity: number; revenue: number }>();

  if (!topProdErr && topProductsData) {
    topProductsData.forEach((item: any) => {
      const key = item.sku || item.product_name || 'unknown';
      const existing = productsMap.get(key) || {
        sku: item.sku || '',
        product_name: item.product_name || 'Unknown Product',
        quantity: 0,
        revenue: 0,
      };

      existing.quantity += item.quantity || 0;
      existing.revenue += Number(item.total_price || 0);
      productsMap.set(key, existing);
    });
  }

  const topProducts = Array.from(productsMap.values())
    .sort((a, b) => b.revenue - a.revenue)
    .slice(0, 5);

  // 5. Stock Alerts Placeholder (or pull from mock/db)
  const lowStockAlerts = [
    { sku: 'TS-BLK-M', product_name: 'TikTok Shop Classic Tee Black M', stock: 3, platform: 'tiktok-shop' },
    { sku: 'SH-PNT-BLU', product_name: 'Shopee Denim Pants Blue L', stock: 1, platform: 'shopee' }
  ];

  return {
    summary: {
      totalRevenueToday,
      totalOrdersToday,
      adSpendToday,
      roasToday,
    },
    platformBreakdown,
    hourlyTrends,
    topProducts,
    lowStockAlerts,
    platformHealth: healthMap,
    lastUpdatedAt: new Date().toISOString(),
  };
}
```

---

### File: `apps/backend/src/services/token.service.ts`

```typescript
import { supabase } from '../db/supabase';
import { encryptSecret, decryptSecret } from '../utils/crypto';

interface DecryptedToken {
  accessToken: string;
  refreshToken?: string;
  expiresAt?: Date;
}

export async function getConnectionToken(connectionId: string): Promise<DecryptedToken> {
  const { data, error } = await supabase
    .from('platform_tokens')
    .select('access_token, refresh_token, expires_at')
    .eq('connection_id', connectionId)
    .single();

  if (error) {
    throw new Error(`Failed to load token for connection ${connectionId}: ${error.message}`);
  }

  if (!data) {
    throw new Error(`No token found for connection ${connectionId}`);
  }

  return {
    accessToken: decryptSecret(data.access_token),
    refreshToken: data.refresh_token ? decryptSecret(data.refresh_token) : undefined,
    expiresAt: data.expires_at ? new Date(data.expires_at) : undefined,
  };
}

export async function updateConnectionToken(
  connectionId: string,
  accessToken: string,
  refreshToken?: string,
  expiresAt?: Date
): Promise<void> {
  const encryptedAccessToken = encryptSecret(accessToken);
  const encryptedRefreshToken = refreshToken ? encryptSecret(refreshToken) : null;

  // Check if token row already exists
  const { data: existing } = await supabase
    .from('platform_tokens')
    .select('id')
    .eq('connection_id', connectionId)
    .maybeSingle();

  const payload = {
    connection_id: connectionId,
    access_token: encryptedAccessToken,
    refresh_token: encryptedRefreshToken,
    expires_at: expiresAt ? expiresAt.toISOString() : null,
    updated_at: new Date().toISOString(),
  };

  if (existing) {
    const { error } = await supabase
      .from('platform_tokens')
      .update(payload)
      .eq('id', existing.id);

    if (error) throw error;
  } else {
    const { error } = await supabase
      .from('platform_tokens')
      .insert(payload);

    if (error) throw error;
  }

  // Update status in connection to active
  await supabase
    .from('platform_connections')
    .update({
      status: 'active',
      last_connected_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    })
    .eq('id', connectionId);
}
```

---

### File: `apps/backend/src/utils/crypto.ts`

```typescript
import crypto from 'crypto';
import { env } from '../config/env';

const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 12; // 12 bytes for GCM
const TAG_LENGTH = 16; // 16 bytes auth tag

// Generate a 32-byte key from the configured encryption key string
function getEncryptionKey(): Buffer {
  return crypto.createHash('sha256').update(env.ENCRYPTION_KEY).digest();
}

/**
 * Encrypts a plaintext string using AES-256-GCM.
 * Output format: iv_hex:auth_tag_hex:encrypted_text_hex
 */
export function encryptSecret(text: string): string {
  const iv = crypto.randomBytes(IV_LENGTH);
  const key = getEncryptionKey();
  const cipher = crypto.createCipheriv(ALGORITHM, key, iv);

  let encrypted = cipher.update(text, 'utf8', 'hex');
  encrypted += cipher.final('hex');

  const tag = cipher.getAuthTag().toString('hex');

  return `${iv.toString('hex')}:${tag}:${encrypted}`;
}

/**
 * Decrypts a cyphertext string in the format iv_hex:auth_tag_hex:encrypted_text_hex.
 */
export function decryptSecret(encryptedData: string): string {
  try {
    const parts = encryptedData.split(':');
    if (parts.length !== 3) {
      // Graceful fallback for mock tokens or plaintext values in development
      if (encryptedData === 'mock-token' || encryptedData === 'mock-refresh-token' || process.env.MOCK_PLATFORMS === 'true') {
        return encryptedData;
      }
      throw new Error('Invalid encrypted data format');
    }

    const iv = Buffer.from(parts[0], 'hex');
    const tag = Buffer.from(parts[1], 'hex');
    const encryptedText = Buffer.from(parts[2], 'hex');

    const key = getEncryptionKey();
    const decipher = crypto.createDecipheriv(ALGORITHM, key, iv);
    decipher.setAuthTag(tag);

    const decrypted = Buffer.concat([
      decipher.update(encryptedText),
      decipher.final()
    ]).toString('utf8');

    return decrypted;
  } catch (error: any) {
    // If it's development/mock, return the raw value as a safety fallback
    if (process.env.MOCK_PLATFORMS === 'true') {
      return encryptedData;
    }
    throw new Error(`Failed to decrypt secret: ${error.message}`);
  }
}
```

---

### File: `apps/dashboard/src/App.tsx`

```tsx
import { useState, useEffect, useRef } from 'react';
import DashboardPage from './components/dashboard/DashboardPage';
import { generateLiveMockData } from './data/mockDashboardData';
import { DashboardData } from './types/dashboard';
import { RefreshCw } from 'lucide-react';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const POLLING_INTERVAL_MS = 30 * 1000; // 30 seconds

export default function App() {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);

  // Reference to cache previous valid data for fail-safe rendering
  const lastValidDataRef = useRef<DashboardData | null>(null);

  // Fetch metrics handler
  const fetchMetrics = async (forceRefreshIndicator = false) => {
    if (forceRefreshIndicator) setIsRefreshing(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/dashboard/live`);
      if (!response.ok) {
        throw new Error(`API returned HTTP ${response.status}`);
      }
      const json: DashboardData = await response.json();
      
      setData(json);
      lastValidDataRef.current = json;
      setError(null);
    } catch (err: any) {
      console.warn('Backend API is offline or unconfigured. Falling back to simulated live mock data.', err.message);
      setError(`Backend API offline. Using simulated live metrics.`);
      
      // Fallback: Use last cached data, or generate dynamic mock data simulating live dashboard activity
      if (lastValidDataRef.current) {
        const nextMock = generateLiveMockData();
        // Keep platform connection states matching previous details
        nextMock.platformHealth = lastValidDataRef.current.platformHealth;
        setData(nextMock);
        lastValidDataRef.current = nextMock;
      } else {
        const initialMock = generateLiveMockData();
        setData(initialMock);
        lastValidDataRef.current = initialMock;
      }
    } finally {
      setLoading(false);
      setIsRefreshing(false);
    }
  };

  // Initial fetch and 30-second polling loop
  useEffect(() => {
    fetchMetrics();
    const interval = setInterval(() => fetchMetrics(), POLLING_INTERVAL_MS);
    return () => clearInterval(interval);
  }, []);

  // Display loader on first loading cycle if no data exists
  if (loading && !data) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen gap-3 bg-slate-950 text-slate-300">
        <RefreshCw className="animate-spin text-blue-500" size={42} />
        <h2 className="font-display font-semibold text-sm tracking-wider text-slate-400">LOADING MDATA DASHBOARD v2...</h2>
      </div>
    );
  }

  // Active dashboard view (guaranteed to have loaded data or fallback)
  return (
    <DashboardPage
      data={data!}
      isRefreshing={isRefreshing}
      onRefresh={() => fetchMetrics(true)}
      error={error}
    />
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/AdCostDonutCard.tsx`

```tsx
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { formatVND } from '../../utils/formatCurrency';

interface Segment {
  label: string;
  valueField: string;
  colorName: string;
}

interface AdCostDonutCardProps {
  title: string;
  subtitle: string;
  value: number; // total spend
  segments: Segment[];
  data: Record<string, number>;
  apiSources: string[];
}

export default function AdCostDonutCard({
  title,
  subtitle,
  segments,
  data,
  apiSources
}: AdCostDonutCardProps) {
  // Map color names to hex codes
  const colorMap: Record<string, string> = {
    blue: '#1877f2',   // Meta Ads
    cyan: '#06b6d4',   // TikTok Ads (cyan)
    orange: '#ff5722', // Shopee Ads
  };

  const chartData = segments.map((seg) => {
    const val = data[seg.valueField] || 0;
    return {
      name: seg.label,
      value: val,
      color: colorMap[seg.colorName] || '#94a3b8',
    };
  });

  const totalValue = chartData.reduce((acc, curr) => acc + curr.value, 0);

  return (
    <div className="relative group overflow-hidden rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md p-5 flex flex-col shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300">
      <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 mb-4">
        <div>
          <h3 className="text-xs font-bold text-slate-400 tracking-wider uppercase font-display">{title}</h3>
          <span className="text-[9px] text-slate-500 font-semibold uppercase tracking-wider">{subtitle}</span>
        </div>
        <div className="relative">
          <span className="text-[10px] text-slate-500 font-mono hover:text-slate-300 cursor-pointer">
            API SOURCES
          </span>
          <div className="pointer-events-none absolute bottom-full right-0 mb-2 w-52 p-2 bg-slate-950 border border-slate-800 rounded-lg shadow-xl opacity-0 group-hover:opacity-100 transition-opacity duration-200 z-50 text-[9px] text-slate-300 space-y-1">
            <div className="font-bold text-white border-b border-slate-800 pb-1 mb-1">API Sources:</div>
            {apiSources.map((src, idx) => (
              <div key={idx} className="flex items-start gap-1">
                <span className="text-blue-400">•</span>
                <span>{src}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="flex items-center justify-between gap-4 flex-1">
        {/* Donut Chart */}
        <div className="relative w-1/2 h-[140px] flex items-center justify-center">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={chartData}
                cx="50%"
                cy="50%"
                innerRadius={45}
                outerRadius={60}
                paddingAngle={4}
                dataKey="value"
              >
                {chartData.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={entry.color} />
                ))}
              </Pie>
              <Tooltip 
                formatter={(val: number) => [formatVND(val), 'Chi phí']}
                contentStyle={{ backgroundColor: '#020617', borderColor: '#1e293b', borderRadius: 8, fontSize: 10 }}
              />
            </PieChart>
          </ResponsiveContainer>
          
          {/* Central text overlay */}
          <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none text-center">
            <span className="text-[8px] font-bold text-slate-500 uppercase tracking-widest">TỔNG</span>
            <span className="text-sm font-extrabold text-white font-display mt-0.5">{formatVND(totalValue)}</span>
          </div>
        </div>

        {/* Legend listing */}
        <div className="w-1/2 flex flex-col gap-2.5">
          {chartData.map((item, idx) => {
            const pct = totalValue > 0 ? (item.value / totalValue) * 100 : 0;
            return (
              <div key={idx} className="flex flex-col">
                <div className="flex items-center gap-1.5 justify-between">
                  <div className="flex items-center gap-1.5">
                    <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: item.color }} />
                    <span className="text-[10px] font-bold text-slate-300 truncate max-w-[80px]">{item.name}</span>
                  </div>
                  <span className="text-[10px] font-mono font-bold text-white">{pct.toFixed(0)}%</span>
                </div>
                <span className="text-[9px] font-mono text-slate-500 pl-4">{formatVND(item.value)}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/DashboardHeader.tsx`

```tsx
import { useState, useEffect } from 'react';
import FullscreenButton from './FullscreenButton';
import { formatDate, formatTime, getMinutesDifference } from '../../utils/time';
import { AlertTriangle, RefreshCw, BarChart2 } from 'lucide-react';

interface DashboardHeaderProps {
  lastUpdatedAt: string;
  isRefreshing: boolean;
  onRefresh: () => void;
  error?: string | null;
}

export default function DashboardHeader({
  lastUpdatedAt,
  isRefreshing,
  onRefresh,
  error
}: DashboardHeaderProps) {
  const [time, setTime] = useState(new Date());

  useEffect(() => {
    const timer = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  const diffMin = getMinutesDifference(lastUpdatedAt);
  const isStale = diffMin >= 15;

  return (
    <header className="relative w-full rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md px-5 py-3 flex flex-col md:flex-row md:items-center justify-between gap-3 shadow-lg shadow-slate-950/20">
      <div className="flex items-center gap-3">
        <div className="p-2 rounded-lg bg-blue-600/10 border border-blue-500/20 text-blue-400">
          <BarChart2 size={22} />
        </div>
        <div>
          <h1 className="text-lg font-black tracking-tight text-white font-display flex items-center gap-2">
            MData <span className="px-1.5 py-0.5 rounded text-[8px] font-extrabold bg-blue-500/10 text-blue-400 border border-blue-500/20 uppercase tracking-widest">LIVE v2</span>
          </h1>
          <p className="text-[9px] text-slate-500 font-semibold uppercase tracking-wider">
            API-Supported Dashboard v2 • Treo văn phòng 24/7
          </p>
        </div>
      </div>

      {/* Warnings / Alerts */}
      <div className="flex flex-col md:flex-row items-center gap-2">
        {error && (
          <div className="flex items-center gap-1.5 px-3 py-1 rounded-lg text-[10px] font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
            <AlertTriangle size={12} className="animate-pulse" />
            <span>Mất kết nối API (Mockup preview)</span>
          </div>
        )}
        
        {isStale && (
          <div className="flex items-center gap-1.5 px-3 py-1 rounded-lg text-[10px] font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20 animate-pulse">
            <AlertTriangle size={12} />
            <span>Dữ liệu chậm ({diffMin} phút trước)</span>
          </div>
        )}

        <div className="flex items-center gap-4">
          <div className="text-right">
            <div className="text-sm font-extrabold text-white font-mono leading-none">
              {formatTime(time.toISOString())}
            </div>
            <div className="text-[8px] text-slate-500 font-medium mt-0.5">
              {formatDate(time)}
            </div>
          </div>

          <div className="flex items-center gap-1.5">
            <button
              onClick={onRefresh}
              disabled={isRefreshing}
              className="p-2 rounded-lg bg-slate-800/50 hover:bg-slate-700/50 border border-slate-700 text-slate-300 hover:text-white transition disabled:opacity-50"
              title="Cập nhật ngay"
            >
              <RefreshCw className={isRefreshing ? 'animate-spin' : ''} size={18} />
            </button>

            <FullscreenButton />
          </div>
        </div>
      </div>
    </header>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/DashboardPage.tsx`

```tsx
import DashboardHeader from './DashboardHeader';
import KpiCard from './KpiCard';
import AdCostDonutCard from './AdCostDonutCard';
import HourlyAdCostLineChart from './HourlyAdCostLineChart';
import MiniMetricGrid from './MiniMetricGrid';
import RealtimeOrdersTable from './RealtimeOrdersTable';
import TopAdCampaignsTable from './TopAdCampaignsTable';
import RevenueByChannelTable from './RevenueByChannelTable';
import HourlyRevenueLineChart from './HourlyRevenueLineChart';
import TopProductsTable from './TopProductsTable';
import RevenueShareDonutCard from './RevenueShareDonutCard';
import DataSourceFooter from './DataSourceFooter';
import { DashboardData } from '../../types/dashboard';

interface DashboardPageProps {
  data: DashboardData;
  isRefreshing: boolean;
  onRefresh: () => void;
  error?: string | null;
}

export default function DashboardPage({
  data,
  isRefreshing,
  onRefresh,
  error
}: DashboardPageProps) {
  // Config layout models matching exactly our new v2 JSON layout configuration
  const kpiCardsConfig = [
    {
      title: "DOANH THU HÔM NAY",
      valueField: "revenueToday" as const,
      availabilityType: "direct_api",
      unit: "đ",
      apiSources: ["Haravan Orders API", "Shopee Orders API", "TikTok Shop Orders API"],
      logic: "Tổng doanh thu phát sinh hôm nay qua các sàn."
    },
    {
      title: "ĐƠN HÀNG HÔM NAY",
      valueField: "ordersToday" as const,
      availabilityType: "direct_api",
      unit: "",
      apiSources: ["Haravan Webhook", "Shopee Orders API", "TikTok Shop Webhook"],
      logic: "Tổng số lượng đơn hàng nhận được hôm nay."
    },
    {
      title: "ĐƠN HÀNG 1H QUA",
      valueField: "ordersLastHour" as const,
      availabilityType: "direct_api",
      unit: "",
      apiSources: ["Realtime Orders stream"],
      logic: "Số lượng đơn hàng phát sinh trong vòng 1 giờ qua."
    },
    {
      title: "GIÁ TRỊ ĐƠN TB",
      valueField: "averageOrderValue" as const,
      availabilityType: "partial_api",
      unit: "đ",
      apiSources: ["Derived from Revenue & Orders"],
      logic: "Doanh thu hôm nay chia cho số lượng đơn hàng."
    },
    {
      title: "CHI PHÍ QUẢNG CÁO HÔM NAY",
      valueField: "adSpendToday" as const,
      availabilityType: "direct_api",
      unit: "đ",
      apiSources: ["Meta Ads Insights API", "TikTok API for Business", "Shopee Marketing API if approved"],
      logic: "Tổng chi phí quảng cáo trong ngày từ các ad platform đã kết nối."
    }
  ];

  const donutConfig = {
    title: "CHI PHÍ QUẢNG CÁO",
    subtitle: "HÔM NAY",
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
    subtitle: "HÔM NAY",
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
    <main className="dashboard-page mdata-dashboard api-supported-layout dark-theme min-h-screen bg-gradient-to-br from-slate-950 via-[#0a0f1d] to-[#030712] p-2 flex flex-col gap-2 select-none">
      {/* Header bar */}
      <DashboardHeader
        lastUpdatedAt={data.lastUpdatedAt}
        isRefreshing={isRefreshing}
        onRefresh={onRefresh}
        error={error}
      />

      {/* Row 1: KPI Summary Row */}
      <section className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2">
        {kpiCardsConfig.map((card) => {
          const val = data[card.valueField as keyof DashboardData] as number;
          return (
            <KpiCard
              key={card.valueField}
              title={card.title}
              value={val}
              unit={card.unit}
              availabilityType={card.availabilityType}
              apiSources={card.apiSources}
              logic={card.logic}
            />
          );
        })}
      </section>

      {/* Row 2: Sales Channels, Ads and Mini Metrics */}
      <section className="grid grid-cols-1 lg:grid-cols-[33%_33%_34%] gap-2">
        <RevenueByChannelTable
          title="DOANH THU THEO KÊNH"
          data={data.revenueByChannel}
          apiSources={["Haravan Orders API", "Shopee Orders API", "TikTok Shop Orders API"]}
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
      <section className="grid grid-cols-1 lg:grid-cols-[33%_33%_34%] gap-2">
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

      {/* Row 4: Top Products, Campaign Table and Revenue Share Donut */}
      <section className="grid grid-cols-1 lg:grid-cols-[33%_33%_34%] gap-2">
        <TopProductsTable
          title="SẢN PHẨM BÁN CHẠY"
          data={data.topProducts}
        />
        <TopAdCampaignsTable
          title={topAdCampaignsConfig.title}
          subtitle={topAdCampaignsConfig.subtitle}
          columns={topAdCampaignsConfig.table.columns}
          data={data.topAdCampaigns}
          apiSources={topAdCampaignsConfig.apiSources}
          note={topAdCampaignsConfig.note}
        />
        <RevenueShareDonutCard
          title="TỶ TRỌNG DOANH THU THEO KÊNH"
          data={data.revenueByChannel}
        />
      </section>

      {/* Footer bar with Data Source Health Statuses */}
      <DataSourceFooter
        platformHealth={data.platformHealth}
        lastUpdatedAt={data.lastUpdatedAt}
        isRefreshing={isRefreshing}
        onRefresh={onRefresh}
      />
    </main>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/DataSourceFooter.tsx`

```tsx
import { PlatformHealthDetails } from '../../types/dashboard';
import { formatTime } from '../../utils/time';
import { RefreshCw } from 'lucide-react';

interface DataSourceFooterProps {
  platformHealth: {
    haravan: PlatformHealthDetails;
    shopee: PlatformHealthDetails;
    tiktokShop: PlatformHealthDetails;
    tiktokAds: PlatformHealthDetails;
    metaAds: PlatformHealthDetails;
  };
  lastUpdatedAt: string;
  isRefreshing: boolean;
  onRefresh: () => void;
}

export default function DataSourceFooter({
  platformHealth,
  lastUpdatedAt,
  isRefreshing,
  onRefresh
}: DataSourceFooterProps) {
  const getStatusDot = (status: string) => {
    switch (status) {
      case 'ok':
        return 'bg-emerald-500';
      case 'warning':
        return 'bg-amber-500';
      case 'error':
        return 'bg-rose-500';
      default:
        return 'bg-slate-500';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'ok':
        return 'text-emerald-400';
      case 'warning':
        return 'text-amber-400';
      case 'error':
        return 'text-rose-400';
      default:
        return 'text-slate-400';
    }
  };

  return (
    <footer className="mt-2 border border-slate-800/80 bg-slate-900/60 backdrop-blur-md rounded-xl p-3.5 flex flex-col md:flex-row items-center justify-between gap-3 text-[10px] text-slate-400">
      {/* Platform Health Status Grid */}
      <div className="flex flex-wrap items-center justify-center md:justify-start gap-4">
        <span className="font-display font-black text-slate-500 tracking-wider text-[9px] uppercase mr-1">
          NGUỒN DỮ LIỆU:
        </span>
        
        {/* Haravan */}
        <div className="flex items-center gap-1.5 bg-slate-950/40 border border-slate-800/40 px-2 py-1 rounded">
          <span className={`w-1.5 h-1.5 rounded-full ${getStatusDot(platformHealth.haravan.status)}`} />
          <span className="font-semibold uppercase text-slate-300">Haravan:</span>
          <span className={`font-mono font-bold ${getStatusText(platformHealth.haravan.status)}`}>
            {platformHealth.haravan.label}
          </span>
        </div>

        {/* Shopee */}
        <div className="flex items-center gap-1.5 bg-slate-950/40 border border-slate-800/40 px-2 py-1 rounded">
          <span className={`w-1.5 h-1.5 rounded-full ${getStatusDot(platformHealth.shopee.status)}`} />
          <span className="font-semibold uppercase text-slate-300">Shopee:</span>
          <span className={`font-mono font-bold ${getStatusText(platformHealth.shopee.status)}`}>
            {platformHealth.shopee.label}
          </span>
        </div>

        {/* TikTok Shop */}
        <div className="flex items-center gap-1.5 bg-slate-950/40 border border-slate-800/40 px-2 py-1 rounded">
          <span className={`w-1.5 h-1.5 rounded-full ${getStatusDot(platformHealth.tiktokShop.status)}`} />
          <span className="font-semibold uppercase text-slate-300">TikTok Shop:</span>
          <span className={`font-mono font-bold ${getStatusText(platformHealth.tiktokShop.status)}`}>
            {platformHealth.tiktokShop.label}
          </span>
        </div>

        {/* Facebook Ads */}
        <div className="flex items-center gap-1.5 bg-slate-950/40 border border-slate-800/40 px-2 py-1 rounded">
          <span className={`w-1.5 h-1.5 rounded-full ${getStatusDot(platformHealth.metaAds.status)}`} />
          <span className="font-semibold uppercase text-slate-300">Facebook Ads:</span>
          <span className={`font-mono font-bold ${getStatusText(platformHealth.metaAds.status)}`}>
            {platformHealth.metaAds.label}
          </span>
        </div>

        {/* TikTok Ads */}
        <div className="flex items-center gap-1.5 bg-slate-950/40 border border-slate-800/40 px-2 py-1 rounded">
          <span className={`w-1.5 h-1.5 rounded-full ${getStatusDot(platformHealth.tiktokAds.status)}`} />
          <span className="font-semibold uppercase text-slate-300">TikTok Ads:</span>
          <span className={`font-mono font-bold ${getStatusText(platformHealth.tiktokAds.status)}`}>
            {platformHealth.tiktokAds.label}
          </span>
        </div>
      </div>

      {/* Sync State & Action Button */}
      <div className="flex items-center gap-3">
        <span className="font-mono text-slate-500 font-medium">
          CẬP NHẬT LẦN CUỐI: <strong className="text-slate-300">{formatTime(lastUpdatedAt)}</strong>
        </span>
        <button
          onClick={onRefresh}
          disabled={isRefreshing}
          className="flex items-center gap-1 px-2.5 py-1 rounded border border-slate-800 bg-slate-950/50 hover:bg-slate-800 hover:text-white transition duration-200 font-semibold font-display select-none disabled:opacity-50"
        >
          <RefreshCw className={`w-3 h-3 ${isRefreshing ? 'animate-spin text-blue-500' : ''}`} />
          LÀM MỚI
        </button>
      </div>
    </footer>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/FullscreenButton.tsx`

```tsx
import { useState, useEffect } from 'react';
import { Maximize2, Minimize2 } from 'lucide-react';

export default function FullscreenButton() {
  const [isFullscreen, setIsFullscreen] = useState(false);

  useEffect(() => {
    const onFullscreenChange = () => {
      setIsFullscreen(!!document.fullscreenElement);
    };
    document.addEventListener('fullscreenchange', onFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', onFullscreenChange);
  }, []);

  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch((err) => {
        console.error(`Error enabling fullscreen: ${err.message}`);
      });
    } else {
      document.exitFullscreen();
    }
  };

  return (
    <button
      onClick={toggleFullscreen}
      className="p-2 rounded-lg bg-slate-800/50 hover:bg-slate-700/50 border border-slate-700 text-slate-300 hover:text-white transition"
      title={isFullscreen ? 'Exit Fullscreen' : 'Enter Fullscreen'}
    >
      {isFullscreen ? <Minimize2 size={18} /> : <Maximize2 size={18} />}
    </button>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/HourlyAdCostLineChart.tsx`

```tsx
import { LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer, Legend } from 'recharts';
import { formatVND } from '../../utils/formatCurrency';

interface Series {
  label: string;
  valueField: string;
  colorName: string;
}

interface HourlyAdCostLineChartProps {
  title: string;
  series: Series[];
  data: any[];
  apiSources: string[];
  note?: string;
}

export default function HourlyAdCostLineChart({
  title,
  series,
  data,
  apiSources,
  note
}: HourlyAdCostLineChartProps) {
  const colorMap: Record<string, string> = {
    blue: '#1877f2',   // Meta Ads
    cyan: '#06b6d4',   // TikTok Ads
    orange: '#ff5722', // Shopee Ads
  };

  return (
    <div className="relative group overflow-hidden rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md p-5 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300">
      <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 mb-4">
        <div>
          <h3 className="text-xs font-bold text-slate-400 tracking-wider uppercase font-display">{title}</h3>
          <span className="text-[8px] text-slate-500 font-semibold uppercase tracking-wider">HẰNG NGÀY THEO GIỜ</span>
        </div>
        <div className="relative">
          <span className="text-[10px] text-slate-500 font-mono hover:text-slate-300 cursor-pointer">
            API SOURCES
          </span>
          <div className="pointer-events-none absolute bottom-full right-0 mb-2 w-52 p-2 bg-slate-950 border border-slate-800 rounded-lg shadow-xl opacity-0 group-hover:opacity-100 transition-opacity duration-200 z-50 text-[9px] text-slate-300 space-y-1">
            <div className="font-bold text-white border-b border-slate-800 pb-1 mb-1">API Sources:</div>
            {apiSources.map((src, idx) => (
              <div key={idx} className="flex items-start gap-1">
                <span className="text-blue-400">•</span>
                <span>{src}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div style={{ width: '100%', height: 140 }}>
        {data && data.length > 0 ? (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ top: 5, right: 10, left: -20, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#1e293b" />
              <XAxis dataKey="hour" stroke="#64748b" fontSize={9} tickLine={false} />
              <YAxis 
                stroke="#64748b" 
                fontSize={9} 
                tickLine={false} 
                axisLine={false}
                tickFormatter={(val) => `${(val / 1000).toFixed(0)}k`}
              />
              <Tooltip 
                formatter={(val: number) => [formatVND(val), 'Chi phí']}
                contentStyle={{ backgroundColor: '#020617', borderColor: '#1e293b', borderRadius: 8, fontSize: 10 }}
              />
              <Legend 
                verticalAlign="top"
                height={20}
                iconType="circle"
                iconSize={6}
                fontSize={9}
                wrapperStyle={{ fontSize: 9, top: -10 }}
              />
              {series.map((s) => (
                <Line
                  key={s.valueField}
                  type="monotone"
                  dataKey={s.valueField}
                  stroke={colorMap[s.colorName] || '#94a3b8'}
                  strokeWidth={2}
                  dot={false}
                  activeDot={{ r: 4 }}
                  name={s.label}
                />
              ))}
            </LineChart>
          </ResponsiveContainer>
        ) : (
          <div className="flex items-center justify-center h-full text-xs text-slate-500">
            Không có dữ liệu chi phí theo giờ
          </div>
        )}
      </div>

      {note && (
        <div className="text-[8px] text-slate-500 italic mt-2 text-right">
          * {note}
        </div>
      )}
    </div>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/HourlyRevenueLineChart.tsx`

```tsx
import { LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid, ResponsiveContainer, Legend } from 'recharts';
import { formatVND } from '../../utils/formatCurrency';
import { BarChart2 } from 'lucide-react';
import { HourlyRevenueItem } from '../../types/dashboard';

interface HourlyRevenueLineChartProps {
  title: string;
  data: HourlyRevenueItem[];
  apiSources: string[];
}

export default function HourlyRevenueLineChart({
  title,
  data,
  apiSources
}: HourlyRevenueLineChartProps) {
  const formatYAxis = (value: number) => {
    if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`;
    if (value >= 1000) return `${(value / 1000).toFixed(0)}k`;
    return String(value);
  };

  return (
    <div className="relative group overflow-hidden rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md p-5 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300 h-full">
      <div>
        <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 mb-3">
          <div className="flex items-center gap-2">
            <BarChart2 size={16} className="text-emerald-500" />
            <div>
              <h3 className="text-xs font-bold text-slate-400 tracking-wider uppercase font-display">
                {title}
              </h3>
              <span className="text-[8px] text-slate-500 font-semibold uppercase tracking-wider">HẰNG NGÀY THEO GIỜ</span>
            </div>
          </div>
          <div className="relative">
            <span className="text-[10px] text-slate-500 font-mono hover:text-slate-300 cursor-pointer">
              API SOURCES
            </span>
            <div className="pointer-events-none absolute bottom-full right-0 mb-2 w-52 p-2 bg-slate-950 border border-slate-800 rounded-lg shadow-xl opacity-0 group-hover:opacity-100 transition-opacity duration-200 z-50 text-[9px] text-slate-300 space-y-1">
              <div className="font-bold text-white border-b border-slate-800 pb-1 mb-1">API Sources:</div>
              {apiSources.map((src, idx) => (
                <div key={idx} className="flex items-start gap-1">
                  <span className="text-blue-400">•</span>
                  <span>{src}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="h-[150px] w-full text-[8px] font-mono mt-2">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart
              data={data}
              margin={{ top: 5, right: 5, left: -20, bottom: 0 }}
            >
              <CartesianGrid strokeDasharray="3 3" stroke="rgba(51, 65, 85, 0.2)" />
              <XAxis 
                dataKey="hour" 
                stroke="#64748b" 
                tickLine={false} 
                axisLine={false}
                tickMargin={6}
              />
              <YAxis 
                stroke="#64748b" 
                tickLine={false} 
                axisLine={false}
                tickFormatter={formatYAxis}
                tickMargin={6}
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: '#020617',
                  border: '1px solid rgba(51, 65, 85, 0.5)',
                  borderRadius: '0.5rem',
                  fontSize: '9px',
                  fontFamily: 'Inter, sans-serif'
                }}
                formatter={(value: number) => [formatVND(value), 'Doanh thu']}
                labelFormatter={(label) => `Thời gian: ${label}`}
              />
              <Legend 
                verticalAlign="top" 
                height={20}
                iconType="circle"
                iconSize={5}
                wrapperStyle={{
                  fontSize: '8px',
                  fontFamily: 'Outfit, Inter, sans-serif',
                  paddingBottom: '5px'
                }}
              />
              <Line 
                name="Hôm nay"
                type="monotone" 
                dataKey="todayRevenue" 
                stroke="#10b981" 
                strokeWidth={2}
                dot={{ r: 1 }}
                activeDot={{ r: 3 }}
              />
              <Line 
                name="Hôm qua"
                type="monotone" 
                dataKey="yesterdayRevenue" 
                stroke="#64748b" 
                strokeWidth={1}
                strokeDasharray="4 4"
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/KpiCard.tsx`

```tsx
import { formatVND } from '../../utils/formatCurrency';
import { formatNumber } from '../../utils/formatNumber';
import { Info } from 'lucide-react';

interface KpiCardProps {
  title: string;
  value: number;
  unit: string;
  availabilityType: string;
  apiSources: string[];
  logic: string;
  note?: string;
}

export default function KpiCard({
  title,
  value,
  unit,
  availabilityType,
  apiSources,
  logic,
  note
}: KpiCardProps) {
  // Automatic value styling
  const isCurrency = unit === 'đ' || title.toLowerCase().includes('chi phí') || title.toLowerCase().includes('lợi nhuận');
  const isRoas = title.toLowerCase().includes('roas');

  const formattedValue = isCurrency 
    ? formatVND(value) 
    : isRoas 
      ? `${value.toFixed(2)}x` 
      : formatNumber(value);

  // Get availability badge styling
  const getAvailabilityClass = (type: string) => {
    switch (type) {
      case 'direct_api':
        return 'text-emerald-400 border-emerald-500/30 bg-emerald-500/5';
      case 'partial_api':
        return 'text-sky-400 border-sky-500/30 bg-sky-500/5';
      case 'requires_tracking':
        return 'text-purple-400 border-purple-500/30 bg-purple-500/5';
      case 'requires_internal_mapping':
        return 'text-amber-400 border-amber-500/30 bg-amber-500/5';
      default:
        return 'text-slate-400 border-slate-500/30 bg-slate-500/5';
    }
  };

  return (
    <div className="relative group overflow-hidden rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md p-5 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300">
      {/* Glow effect on hover */}
      <div className="absolute inset-0 -z-10 bg-gradient-to-br from-blue-600/5 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />
      
      <div className="flex items-start justify-between gap-4">
        <span className="text-[10px] tracking-wider font-extrabold text-slate-400 uppercase font-display">
          {title}
        </span>
        <span className={`px-2 py-0.5 rounded text-[8px] font-mono font-bold border uppercase tracking-wider ${getAvailabilityClass(availabilityType)}`}>
          {availabilityType.replace('_', ' ')}
        </span>
      </div>

      <div className="my-3 flex items-baseline gap-1">
        <span className="text-3xl font-extrabold tracking-tight text-white font-display">
          {formattedValue}
        </span>
      </div>

      <div className="flex items-center justify-between mt-1 text-[10px] text-slate-500 border-t border-slate-800/50 pt-2">
        <div className="flex items-center gap-1.5 cursor-help max-w-[85%]" title={`${logic} ${note ? `• Chú ý: ${note}` : ''}`}>
          <Info size={12} className="text-slate-400 shrink-0" />
          <span className="truncate">{logic}</span>
        </div>
        <div className="relative">
          <span className="text-slate-500 font-medium hover:text-slate-300 cursor-pointer font-mono">
            API ({apiSources.length})
          </span>
          {/* Tooltip on hover */}
          <div className="pointer-events-none absolute bottom-full right-0 mb-2 w-52 p-2 bg-slate-950 border border-slate-800 rounded-lg shadow-xl opacity-0 group-hover:opacity-100 transition-opacity duration-200 z-50 text-[9px] text-slate-300 space-y-1">
            <div className="font-bold text-white border-b border-slate-800 pb-1 mb-1">Nguồn dữ liệu:</div>
            {apiSources.map((src, idx) => (
              <div key={idx} className="flex items-start gap-1">
                <span className="text-blue-400">•</span>
                <span>{src}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/MiniMetricGrid.tsx`

```tsx
import { formatVND } from '../../utils/formatCurrency';
import { formatNumber } from '../../utils/formatNumber';

interface MiniCard {
  cardId: string;
  name: string;
  title: string;
  note?: string;
  availabilityType: string;
  valueField: string;
  unit: string;
}

interface MiniMetricGridProps {
  cards: MiniCard[];
  data: Record<string, any>;
}

export default function MiniMetricGrid({ cards, data }: MiniMetricGridProps) {
  const getAvailabilityClass = (type: string) => {
    switch (type) {
      case 'direct_api':
        return 'text-emerald-400 border-emerald-500/20 bg-emerald-500/5';
      case 'partial_api':
        return 'text-sky-400 border-sky-500/20 bg-sky-500/5';
      case 'requires_tracking':
        return 'text-purple-400 border-purple-500/20 bg-purple-500/5';
      case 'requires_internal_mapping':
        return 'text-amber-400 border-amber-500/20 bg-amber-500/5';
      default:
        return 'text-slate-400 border-slate-500/20 bg-slate-500/5';
    }
  };

  return (
    <div className="grid grid-cols-2 grid-rows-2 gap-2 h-full">
      {cards.map((card) => {
        const rawValue = data[card.valueField] ?? 0;
        const isMer = card.title.toLowerCase().includes('mer');
        const isRoas = card.title.toLowerCase().includes('roas');
        const isMultiplier = isRoas || isMer || card.unit === 'x';
        const isPercent = card.unit === '%';
        const isCurrency = card.unit === 'đ' || card.title.toLowerCase().includes('lợi nhuận');

        const formatted = isCurrency
          ? formatVND(rawValue)
          : isMultiplier
            ? `${Number(rawValue).toFixed(2)}x`
            : isPercent
              ? `${rawValue}%`
              : formatNumber(rawValue);

        return (
          <div
            key={card.cardId}
            className="rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md p-3.5 flex flex-col justify-between hover:border-slate-700/80 transition duration-300 shadow-md shadow-slate-950/10"
          >
            <div className="flex justify-between items-start gap-1">
              <span className="text-[9px] font-extrabold text-slate-400 tracking-wider uppercase font-display">
                {card.title} {card.note && <span className="text-[7px] text-slate-500 font-bold font-body">• {card.note}</span>}
              </span>
            </div>

            <div className="my-2">
              <span className="text-xl font-extrabold tracking-tight text-white font-display">
                {formatted}
              </span>
            </div>

            <div className="flex justify-between items-center text-[7px] text-slate-500 font-mono">
              <span className={`px-1 py-0.5 rounded border uppercase ${getAvailabilityClass(card.availabilityType)}`}>
                {card.availabilityType.split('_')[0]} API
              </span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/PlatformHealthCard.tsx`

```tsx
import StatusBadge from './StatusBadge';
import { formatTime } from '../../utils/time';

interface PlatformHealthCardProps {
  title: string;
  availabilityType: string;
  status?: 'ok' | 'warning' | 'error' | 'unknown';
  lastSuccessAt?: string | null;
  lastErrorAt?: string | null;
  
  // Custom properties for ads_health
  isAdsHealth?: boolean;
  metaStatus?: 'ok' | 'warning' | 'error' | 'unknown';
  tiktokAdsStatus?: 'ok' | 'warning' | 'error' | 'unknown';
  lastUpdatedAt?: string | null;
}

export default function PlatformHealthCard({
  title,
  availabilityType,
  status = 'unknown',
  lastSuccessAt,
  lastErrorAt,
  isAdsHealth = false,
  metaStatus = 'unknown',
  tiktokAdsStatus = 'unknown',
  lastUpdatedAt
}: PlatformHealthCardProps) {
  const formatDateTimeShort = (isoString?: string | null) => {
    if (!isoString) return 'Chưa đồng bộ';
    const d = new Date(isoString);
    return `${d.toLocaleDateString('vi-VN', { month: '2-digit', day: '2-digit' })} ${formatTime(isoString)}`;
  };


  return (
    <div className="relative group overflow-hidden rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md p-4 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300">
      <div className="flex items-center justify-between border-b border-slate-800/40 pb-2 mb-3">
        <span className="text-[10px] tracking-wider font-extrabold text-slate-400 uppercase font-display">
          {title}
        </span>
        <span className="text-[7px] font-mono text-slate-600 uppercase tracking-widest">
          {availabilityType.replace('_', ' ')}
        </span>
      </div>

      {!isAdsHealth ? (
        // Standard Channel Health Details
        <div className="flex flex-col gap-2.5">
          <div className="flex items-center justify-between">
            <span className="text-[10px] text-slate-400">Trạng thái:</span>
            <StatusBadge status={status} size={12} />
          </div>
          
          <div className="space-y-1">
            <div className="flex justify-between items-center text-[9px] text-slate-500">
              <span>Đồng bộ cuối:</span>
              <span className="font-mono text-slate-300">{formatDateTimeShort(lastSuccessAt)}</span>
            </div>
            {status !== 'ok' && lastErrorAt && (
              <div className="flex justify-between items-center text-[9px] text-rose-500 bg-rose-500/5 px-1 py-0.5 rounded border border-rose-500/10">
                <span>Lỗi cuối:</span>
                <span className="font-mono font-medium">{formatDateTimeShort(lastErrorAt)}</span>
              </div>
            )}
          </div>
        </div>
      ) : (
        // Custom Advertising Accounts Health (Meta + TikTok)
        <div className="flex flex-col gap-2.5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-[#1877f2]" />
              <span className="text-[9px] text-slate-300 font-bold uppercase font-display">Meta Ads</span>
            </div>
            <StatusBadge status={metaStatus} size={10} />
          </div>

          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-cyan-400" />
              <span className="text-[9px] text-slate-300 font-bold uppercase font-display">TikTok Ads</span>
            </div>
            <StatusBadge status={tiktokAdsStatus} size={10} />
          </div>

          <div className="flex justify-between items-center text-[9px] text-slate-500 border-t border-slate-800/40 pt-1.5 mt-0.5">
            <span>Cập nhật lúc:</span>
            <span className="font-mono text-slate-300">{formatDateTimeShort(lastUpdatedAt)}</span>
          </div>
        </div>
      )}
    </div>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/RealtimeOrdersTable.tsx`

```tsx
import { RealtimeOrder } from '../../types/dashboard';
import { formatVND } from '../../utils/formatCurrency';
import { formatTime } from '../../utils/time';
import { ShoppingBag } from 'lucide-react';

interface Column {
  label: string;
  field: string;
}

interface RealtimeOrdersTableProps {
  title: string;
  note: string;
  columns: Column[];
  data: RealtimeOrder[];
  apiSources: string[];
  logic: string;
}

export default function RealtimeOrdersTable({
  title,
  note,
  columns,
  data,
  apiSources,
  logic
}: RealtimeOrdersTableProps) {
  const getPlatformBadge = (platform: string) => {
    switch (platform) {
      case 'haravan':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-bold bg-[#004b91]/10 text-blue-400 border border-[#004b91]/20">
            Haravan
          </span>
        );
      case 'shopee':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-bold bg-[#ff5722]/10 text-orange-400 border border-[#ff5722]/20">
            Shopee
          </span>
        );
      case 'tiktok-shop':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-bold bg-white/5 text-white border border-white/10">
            TikTok Shop
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-bold bg-slate-500/10 text-slate-400 border border-slate-500/20">
            {platform}
          </span>
        );
    }
  };

  return (
    <div className="relative group overflow-hidden rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md p-5 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300 h-full">
      <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 mb-3">
        <div className="flex items-center gap-2">
          <ShoppingBag size={16} className="text-blue-500" />
          <div>
            <h3 className="text-xs font-bold text-slate-400 tracking-wider uppercase font-display">
              {title} <span className="text-[8px] text-rose-500 font-bold font-body animate-pulse">• {note}</span>
            </h3>
            <span className="text-[8px] text-slate-500 font-semibold uppercase tracking-wider">{logic}</span>
          </div>
        </div>
        <div className="relative">
          <span className="text-[10px] text-slate-500 font-mono hover:text-slate-300 cursor-pointer">
            API SOURCES
          </span>
          <div className="pointer-events-none absolute bottom-full right-0 mb-2 w-52 p-2 bg-slate-950 border border-slate-800 rounded-lg shadow-xl opacity-0 group-hover:opacity-100 transition-opacity duration-200 z-50 text-[9px] text-slate-300 space-y-1">
            <div className="font-bold text-white border-b border-slate-800 pb-1 mb-1">API Sources:</div>
            {apiSources.map((src, idx) => (
              <div key={idx} className="flex items-start gap-1">
                <span className="text-blue-400">•</span>
                <span>{src}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="overflow-auto max-h-[220px] flex-1">
        <table className="w-full border-collapse text-left">
          <thead>
            <tr className="border-b border-slate-800">
              {columns.map((col) => (
                <th key={col.field} className="pb-2 text-[9px] font-bold text-slate-500 uppercase tracking-wider">
                  {col.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/40">
            {data.map((row) => (
              <tr key={row.id} className="hover:bg-slate-800/20 transition-colors">
                <td className="py-2 text-[10px] font-mono text-slate-400">
                  {formatTime(row.createdAt)}
                </td>
                <td className="py-2 text-[10px] font-mono text-slate-200 font-semibold">
                  {row.orderCode}
                </td>
                <td className="py-2 text-[10px] text-slate-300 truncate max-w-[100px]">
                  {row.customerDisplayName}
                </td>
                <td className="py-2">
                  {getPlatformBadge(row.platform)}
                </td>
                <td className="py-2 text-[10px] font-mono text-white font-extrabold text-right">
                  {formatVND(row.orderValue)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/RevenueByChannelTable.tsx`

```tsx
import { RevenueByChannelItem } from '../../types/dashboard';
import { formatVND } from '../../utils/formatCurrency';
import { Store, TrendingUp } from 'lucide-react';

interface RevenueByChannelTableProps {
  title: string;
  data: RevenueByChannelItem[];
  apiSources: string[];
}

export default function RevenueByChannelTable({
  title,
  data,
  apiSources
}: RevenueByChannelTableProps) {
  const getPlatformBadge = (platform: string) => {
    switch (platform) {
      case 'haravan':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[8px] font-bold bg-blue-600/10 text-blue-400 border border-blue-500/20 uppercase">
            Haravan
          </span>
        );
      case 'shopee':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[8px] font-bold bg-[#ff5722]/10 text-orange-400 border border-[#ff5722]/20 uppercase">
            Shopee
          </span>
        );
      case 'tiktok-shop':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[8px] font-bold bg-purple-500/10 text-purple-400 border border-purple-500/20 uppercase">
            TikTok Shop
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[8px] font-bold bg-slate-500/10 text-slate-400 border border-slate-500/20 uppercase">
            {platform}
          </span>
        );
    }
  };

  return (
    <div className="relative group overflow-hidden rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md p-5 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300 h-full">
      <div>
        <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 mb-3">
          <div className="flex items-center gap-2">
            <Store size={16} className="text-blue-500" />
            <div>
              <h3 className="text-xs font-bold text-slate-400 tracking-wider uppercase font-display">
                {title}
              </h3>
              <span className="text-[8px] text-slate-500 font-semibold uppercase tracking-wider">DOANH SỐ HÔM NAY</span>
            </div>
          </div>
          <div className="relative">
            <span className="text-[10px] text-slate-500 font-mono hover:text-slate-300 cursor-pointer">
              API SOURCES
            </span>
            <div className="pointer-events-none absolute bottom-full right-0 mb-2 w-52 p-2 bg-slate-950 border border-slate-800 rounded-lg shadow-xl opacity-0 group-hover:opacity-100 transition-opacity duration-200 z-50 text-[9px] text-slate-300 space-y-1">
              <div className="font-bold text-white border-b border-slate-800 pb-1 mb-1">API Sources:</div>
              {apiSources.map((src, idx) => (
                <div key={idx} className="flex items-start gap-1">
                  <span className="text-blue-400">•</span>
                  <span>{src}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="overflow-auto max-h-[220px]">
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-slate-800">
                <th className="pb-2 text-[9px] font-bold text-slate-500 uppercase tracking-wider">KÊNH</th>
                <th className="pb-2 text-[9px] font-bold text-slate-500 uppercase tracking-wider text-right">DOANH THU</th>
                <th className="pb-2 text-[9px] font-bold text-slate-500 uppercase tracking-wider text-right">TỶ TRỌNG</th>
                <th className="pb-2 text-[9px] font-bold text-slate-500 uppercase tracking-wider text-right">TĂNG TRƯỞNG</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/40">
              {data.map((row) => (
                <tr key={row.platform} className="hover:bg-slate-800/20 transition-colors">
                  <td className="py-2.5 text-[10px] font-semibold text-slate-200 flex items-center gap-1.5">
                    {getPlatformBadge(row.platform)}
                    <span className="truncate max-w-[110px]">{row.label}</span>
                  </td>
                  <td className="py-2.5 text-[10px] font-mono font-bold text-slate-100 text-right">
                    {formatVND(row.revenue)}
                  </td>
                  <td className="py-2.5 text-right">
                    <div className="inline-flex items-center gap-1.5 justify-end w-full">
                      <span className="text-[10px] font-mono text-slate-300 font-bold">{row.share}%</span>
                      <div className="w-12 bg-slate-800 rounded-full h-1 overflow-hidden hidden sm:block">
                        <div 
                          className={`h-full ${
                            row.platform === 'haravan' ? 'bg-blue-500' : row.platform === 'shopee' ? 'bg-orange-500' : 'bg-purple-500'
                          }`}
                          style={{ width: `${row.share}%` }}
                        />
                      </div>
                    </div>
                  </td>
                  <td className="py-2.5 text-[10px] font-mono font-bold text-emerald-400 text-right">
                    <span className="inline-flex items-center gap-0.5 text-[9px] bg-emerald-500/10 text-emerald-400 px-1 py-0.5 rounded font-mono font-extrabold">
                      <TrendingUp size={10} />
                      +{row.changePercent}%
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/RevenueShareDonutCard.tsx`

```tsx
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts';
import { formatVND } from '../../utils/formatCurrency';
import { Activity } from 'lucide-react';
import { RevenueByChannelItem } from '../../types/dashboard';

interface RevenueShareDonutCardProps {
  title: string;
  data: RevenueByChannelItem[];
}

export default function RevenueShareDonutCard({
  title,
  data
}: RevenueShareDonutCardProps) {
  // Map platform to colors matching our overall design system
  const getColor = (platform: string) => {
    switch (platform) {
      case 'haravan':
        return '#3b82f6'; // Blue
      case 'shopee':
        return '#f97316'; // Orange
      case 'tiktok-shop':
        return '#a855f7'; // Purple
      default:
        return '#64748b'; // Muted grey
    }
  };

  const getLabel = (platform: string) => {
    switch (platform) {
      case 'haravan':
        return 'Haravan';
      case 'shopee':
        return 'Shopee';
      case 'tiktok-shop':
        return 'TikTok Shop';
      default:
        return platform;
    }
  };

  const chartData = data.map((item) => ({
    name: getLabel(item.platform),
    value: item.revenue,
    share: item.share,
    color: getColor(item.platform)
  }));

  const totalRevenue = data.reduce((acc, curr) => acc + curr.revenue, 0);

  return (
    <div className="relative group overflow-hidden rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md p-5 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300 h-full">
      <div>
        <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 mb-3">
          <div className="flex items-center gap-2">
            <Activity size={16} className="text-blue-400" />
            <div>
              <h3 className="text-xs font-bold text-slate-400 tracking-wider uppercase font-display">
                {title}
              </h3>
              <span className="text-[8px] text-slate-500 font-semibold uppercase tracking-wider">DOANH SỐ HÔM NAY</span>
            </div>
          </div>
        </div>

        <div className="flex items-center justify-between gap-4 mt-2">
          {/* Chart Wrapper */}
          <div className="relative w-[110px] h-[110px] shrink-0">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#020617',
                    border: '1px solid rgba(51, 65, 85, 0.5)',
                    borderRadius: '0.5rem',
                    fontSize: '9px',
                    fontFamily: 'Inter, sans-serif'
                  }}
                  formatter={(value: number) => [formatVND(value), 'Doanh thu']}
                />
                <Pie
                  data={chartData}
                  cx="50%"
                  cy="50%"
                  innerRadius={35}
                  outerRadius={50}
                  paddingAngle={3}
                  dataKey="value"
                >
                  {chartData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
              </PieChart>
            </ResponsiveContainer>

            {/* Inner Center Text */}
            <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none select-none">
              <span className="text-[7px] text-slate-500 font-bold uppercase tracking-wider">TỔNG</span>
              <span className="text-[9px] font-mono font-black text-slate-100">
                {totalRevenue >= 1000000 
                  ? `${(totalRevenue / 1000000).toFixed(1)}M` 
                  : `${(totalRevenue / 1000).toFixed(0)}k`
                }
              </span>
            </div>
          </div>

          {/* Legends */}
          <div className="flex-1 space-y-2 text-[9px]">
            {chartData.map((item, idx) => (
              <div key={idx} className="flex items-center justify-between">
                <div className="flex items-center gap-1.5 min-w-0">
                  <span className="w-1.5 h-1.5 rounded-full shrink-0" style={{ backgroundColor: item.color }} />
                  <span className="text-slate-300 font-semibold truncate uppercase">{item.name}</span>
                </div>
                <div className="flex items-baseline gap-1 text-right font-mono font-bold shrink-0">
                  <span className="text-slate-100">{item.share}%</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/StatusBadge.tsx`

```tsx
import { CheckCircle2, AlertTriangle, XCircle, HelpCircle } from 'lucide-react';

interface StatusBadgeProps {
  status: 'ok' | 'warning' | 'error' | 'unknown';
  size?: number;
}

export default function StatusBadge({ status, size = 16 }: StatusBadgeProps) {
  switch (status) {
    case 'ok':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
          <CheckCircle2 size={size} />
          OK
        </span>
      );
    case 'warning':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20 animate-pulse">
          <AlertTriangle size={size} />
          WARN
        </span>
      );
    case 'error':
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
          <XCircle size={size} />
          ERROR
        </span>
      );
    default:
      return (
        <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-500/10 text-slate-400 border border-slate-500/20">
          <HelpCircle size={size} />
          UNKNOWN
        </span>
      );
  }
}
```

---

### File: `apps/dashboard/src/components/dashboard/TopAdCampaignsTable.tsx`

```tsx
import { TopAdCampaign } from '../../types/dashboard';
import { formatVND } from '../../utils/formatCurrency';
import { Layers } from 'lucide-react';

interface Column {
  label: string;
  field: string;
}

interface TopAdCampaignsTableProps {
  title: string;
  subtitle: string;
  columns: Column[];
  data: TopAdCampaign[];
  apiSources: string[];
  note?: string;
}

export default function TopAdCampaignsTable({
  title,
  subtitle,
  columns,
  data,
  apiSources,
  note
}: TopAdCampaignsTableProps) {
  const getPlatformBadge = (platform: string) => {
    switch (platform) {
      case 'facebook':
      case 'facebook-ads':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[8px] font-bold bg-[#1877f2]/10 text-[#1877f2] border border-[#1877f2]/20 uppercase">
            Meta Ads
          </span>
        );
      case 'tiktok':
      case 'tiktok-ads':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[8px] font-bold bg-cyan-500/10 text-cyan-400 border border-cyan-500/20 uppercase">
            TikTok Ads
          </span>
        );
      case 'shopee':
      case 'shopee-ads':
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[8px] font-bold bg-[#ff5722]/10 text-orange-400 border border-[#ff5722]/20 uppercase">
            Shopee Ads
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[8px] font-bold bg-slate-500/10 text-slate-400 border border-slate-500/20 uppercase">
            {platform}
          </span>
        );
    }
  };

  return (
    <div className="relative group overflow-hidden rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md p-5 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300 h-full">
      <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 mb-3">
        <div className="flex items-center gap-2">
          <Layers size={16} className="text-purple-500" />
          <div>
            <h3 className="text-xs font-bold text-slate-400 tracking-wider uppercase font-display">
              {title}
            </h3>
            <span className="text-[8px] text-slate-500 font-semibold uppercase tracking-wider">{subtitle}</span>
          </div>
        </div>
        <div className="relative">
          <span className="text-[10px] text-slate-500 font-mono hover:text-slate-300 cursor-pointer">
            API SOURCES
          </span>
          <div className="pointer-events-none absolute bottom-full right-0 mb-2 w-52 p-2 bg-slate-950 border border-slate-800 rounded-lg shadow-xl opacity-0 group-hover:opacity-100 transition-opacity duration-200 z-50 text-[9px] text-slate-300 space-y-1">
            <div className="font-bold text-white border-b border-slate-800 pb-1 mb-1">API Sources:</div>
            {apiSources.map((src, idx) => (
              <div key={idx} className="flex items-start gap-1">
                <span className="text-blue-400">•</span>
                <span>{src}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="overflow-auto max-h-[220px] flex-1">
        <table className="w-full border-collapse text-left">
          <thead>
            <tr className="border-b border-slate-800">
              {columns.map((col) => (
                <th key={col.field} className={`pb-2 text-[9px] font-bold text-slate-500 uppercase tracking-wider ${col.field === 'attributedOrders' || col.field === 'roas' || col.field === 'spend' ? 'text-right' : ''}`}>
                  {col.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/40">
            {data.map((row) => (
              <tr key={row.campaignName} className="hover:bg-slate-800/20 transition-colors">
                <td className="py-2 text-[10px] text-slate-200 font-semibold truncate max-w-[160px]" title={row.campaignName}>
                  {row.campaignName}
                </td>
                <td className="py-2">
                  {getPlatformBadge(row.platform)}
                </td>
                <td className="py-2 text-[10px] font-mono text-slate-300 text-right">
                  {formatVND(row.spend)}
                </td>
                <td className="py-2 text-[10px] font-mono text-slate-300 text-right">
                  {row.attributedOrders}
                </td>
                <td className={`py-2 text-[10px] font-mono font-extrabold text-right ${row.roas >= 3.0 ? 'text-emerald-400' : 'text-slate-400'}`}>
                  {row.roas.toFixed(2)}x
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {note && (
        <div className="text-[7px] text-slate-500 italic mt-2 text-right">
          * {note}
        </div>
      )}
    </div>
  );
}
```

---

### File: `apps/dashboard/src/components/dashboard/TopProductsTable.tsx`

```tsx
import { TopProductItem } from '../../types/dashboard';
import { formatVND } from '../../utils/formatCurrency';
import { Award } from 'lucide-react';

interface TopProductsTableProps {
  title: string;
  data: TopProductItem[];
}

export default function TopProductsTable({
  title,
  data
}: TopProductsTableProps) {
  const getRankBadge = (rank: number) => {
    switch (rank) {
      case 1:
        return (
          <span className="inline-flex items-center justify-center w-5 h-5 rounded-full text-[10px] font-black bg-amber-500/20 text-amber-400 border border-amber-500/30">
            1
          </span>
        );
      case 2:
        return (
          <span className="inline-flex items-center justify-center w-5 h-5 rounded-full text-[10px] font-black bg-slate-400/20 text-slate-300 border border-slate-400/30">
            2
          </span>
        );
      case 3:
        return (
          <span className="inline-flex items-center justify-center w-5 h-5 rounded-full text-[10px] font-black bg-amber-700/20 text-orange-400 border border-amber-700/30">
            3
          </span>
        );
      default:
        return (
          <span className="inline-flex items-center justify-center w-5 h-5 rounded-full text-[10px] font-bold bg-slate-800 text-slate-400">
            {rank}
          </span>
        );
    }
  };

  return (
    <div className="relative group overflow-hidden rounded-xl border border-slate-800/80 bg-slate-900/60 backdrop-blur-md p-5 flex flex-col justify-between shadow-lg shadow-slate-950/20 hover:border-slate-700/80 transition duration-300 h-full">
      <div>
        <div className="flex justify-between items-start border-b border-slate-800/50 pb-2 mb-3">
          <div className="flex items-center gap-2">
            <Award size={16} className="text-amber-500" />
            <div>
              <h3 className="text-xs font-bold text-slate-400 tracking-wider uppercase font-display">
                {title}
              </h3>
              <span className="text-[8px] text-slate-500 font-semibold uppercase tracking-wider">HÔM NAY</span>
            </div>
          </div>
        </div>

        <div className="overflow-auto max-h-[220px]">
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-slate-800">
                <th className="pb-2 text-[9px] font-bold text-slate-500 uppercase tracking-wider w-12 text-center">HẠNG</th>
                <th className="pb-2 text-[9px] font-bold text-slate-500 uppercase tracking-wider">SẢN PHẨM</th>
                <th className="pb-2 text-[9px] font-bold text-slate-500 uppercase tracking-wider text-right">LƯỢT BÁN</th>
                <th className="pb-2 text-[9px] font-bold text-slate-500 uppercase tracking-wider text-right">DOANH SỐ</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/40">
              {data.map((row) => (
                <tr key={row.rank} className="hover:bg-slate-800/20 transition-colors">
                  <td className="py-2.5 text-center">
                    {getRankBadge(row.rank)}
                  </td>
                  <td className="py-2.5 text-[10px] font-semibold text-slate-200 truncate max-w-[140px]" title={row.productName}>
                    {row.productName}
                  </td>
                  <td className="py-2.5 text-[10px] font-mono font-bold text-slate-300 text-right">
                    {row.orders}
                  </td>
                  <td className="py-2.5 text-[10px] font-mono font-bold text-slate-100 text-right">
                    {formatVND(row.revenue)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
```

---

### File: `apps/dashboard/src/data/mockDashboardData.ts`

```typescript
import { DashboardData } from '../types/dashboard';

export const mockDashboardData: DashboardData = {
  revenueToday: 34820000,
  ordersToday: 212,
  ordersLastHour: 38,
  averageOrderValue: 164245,
  adSpendToday: 5620000,
  estimatedRoas: 6.19,
  estimatedMer: 6.2,
  cancelRate: 2.35,
  returnOrdersToday: 5,

  facebookAdsSpend: 2450000,
  tiktokAdsSpend: 2180000,
  shopeeAdsSpend: 990000,


  revenueByChannel: [
    {
      platform: 'haravan',
      label: 'Haravan (Web/App)',
      revenue: 20450000,
      share: 58.7,
      changePercent: 21.1,
    },
    {
      platform: 'shopee',
      label: 'Shopee',
      revenue: 10280000,
      share: 29.5,
      changePercent: 12.4,
    },
    {
      platform: 'tiktok-shop',
      label: 'TikTok Shop',
      revenue: 3750000,
      share: 10.8,
      changePercent: 7.9,
    },
  ],

  adCostBreakdown: [
    {
      platform: 'facebook-ads',
      label: 'Facebook Ads',
      spend: 2450000,
      share: 43.6,
    },
    {
      platform: 'tiktok-ads',
      label: 'TikTok Ads',
      spend: 2180000,
      share: 38.8,
    },
    {
      platform: 'shopee-ads',
      label: 'Shopee Ads',
      spend: 990000,
      share: 17.6,
    },
  ],

  hourlyRevenue: [
    { hour: '00:00', todayRevenue: 800000, yesterdayRevenue: 500000 },
    { hour: '04:00', todayRevenue: 4200000, yesterdayRevenue: 1800000 },
    { hour: '08:00', todayRevenue: 6100000, yesterdayRevenue: 3200000 },
    { hour: '12:00', todayRevenue: 3500000, yesterdayRevenue: 2900000 },
    { hour: '16:00', todayRevenue: 4700000, yesterdayRevenue: 3800000 },
    { hour: '20:00', todayRevenue: 6800000, yesterdayRevenue: 5100000 },
    { hour: '24:00', todayRevenue: 7800000, yesterdayRevenue: 6000000 },
  ],

  hourlyAdCost: [
    { hour: '00:00', facebookAdsHourlySpend: 250000, tiktokAdsHourlySpend: 150000, shopeeAdsHourlySpend: 80000 },
    { hour: '04:00', facebookAdsHourlySpend: 980000, tiktokAdsHourlySpend: 560000, shopeeAdsHourlySpend: 290000 },
    { hour: '08:00', facebookAdsHourlySpend: 850000, tiktokAdsHourlySpend: 480000, shopeeAdsHourlySpend: 210000 },
    { hour: '12:00', facebookAdsHourlySpend: 420000, tiktokAdsHourlySpend: 300000, shopeeAdsHourlySpend: 140000 },
    { hour: '16:00', facebookAdsHourlySpend: 500000, tiktokAdsHourlySpend: 320000, shopeeAdsHourlySpend: 160000 },
    { hour: '20:00', facebookAdsHourlySpend: 460000, tiktokAdsHourlySpend: 330000, shopeeAdsHourlySpend: 170000 },
    { hour: '24:00', facebookAdsHourlySpend: 620000, tiktokAdsHourlySpend: 390000, shopeeAdsHourlySpend: 220000 },
  ],

  realtimeOrders: [
    {
      id: '1',
      createdAt: new Date(Date.now() - 1000 * 60 * 3).toISOString(),
      orderCode: '#1000458',
      customerDisplayName: 'Nguyễn Minh Anh',
      platform: 'shopee',
      orderValue: 298000,
    },
    {
      id: '2',
      createdAt: new Date(Date.now() - 1000 * 60 * 7).toISOString(),
      orderCode: '#1000457',
      customerDisplayName: 'Trần Hoàng Nam',
      platform: 'tiktok-shop',
      orderValue: 499000,
    },
    {
      id: '3',
      createdAt: new Date(Date.now() - 1000 * 60 * 12).toISOString(),
      orderCode: '#1000456',
      customerDisplayName: 'Lê Thu Trang',
      platform: 'haravan',
      orderValue: 179000,
    },
  ],

  topProducts: [
    { rank: 1, productName: 'Áo Thun Basic Nam Nữ', orders: 86, revenue: 7820000 },
    { rank: 2, productName: 'Quần Short Thể Thao Nam', orders: 64, revenue: 6150000 },
    { rank: 3, productName: 'Áo Hoodie Unisex', orders: 48, revenue: 5280000 },
  ],

  topAdCampaigns: [
    {
      campaignName: 'Summer Sale 2025',
      platform: 'facebook-ads',
      spend: 1250000,
      attributedOrders: 18,
      roas: 4.21,
    },
    {
      campaignName: 'TikTok Viral Video Hook A',
      platform: 'tiktok-ads',
      spend: 980000,
      attributedOrders: 15,
      roas: 5.12,
    },
    {
      campaignName: 'Shopee Campaign',
      platform: 'shopee-ads',
      spend: 720000,
      attributedOrders: 22,
      roas: 3.45,
    },
  ],

  platformHealth: {
    haravan: {
      status: 'ok',
      label: 'LIVE',
      lastSuccessAt: new Date().toISOString(),
    },
    shopee: {
      status: 'ok',
      label: 'LIVE',
      lastSuccessAt: new Date().toISOString(),
    },
    tiktokShop: {
      status: 'ok',
      label: 'LIVE',
      lastSuccessAt: new Date().toISOString(),
    },
    tiktokAds: {
      status: 'warning',
      label: 'Near-time',
      lastSuccessAt: new Date().toISOString(),
    },
    metaAds: {
      status: 'warning',
      label: 'Near-time',
      lastSuccessAt: new Date().toISOString(),
    },
  },

  lastUpdatedAt: new Date().toISOString(),
};

export function generateLiveMockData(): DashboardData {
  const rand = Math.random();
  
  // 1. Increment revenue and order counts
  const addedOrders = rand > 0.6 ? 1 : 0;
  const addedRevenue = addedOrders * Math.floor(150000 + Math.random() * 350000);
  
  const revenueToday = mockDashboardData.revenueToday + addedRevenue;
  const ordersToday = mockDashboardData.ordersToday + addedOrders;
  const ordersLastHour = Math.max(10, mockDashboardData.ordersLastHour + (rand > 0.8 ? 1 : (rand < 0.2 ? -1 : 0)));
  const averageOrderValue = Math.round(revenueToday / ordersToday);
  
  const addedAdSpend = Math.floor(Math.random() * 5000);
  const adSpendToday = mockDashboardData.adSpendToday + addedAdSpend;
  const estimatedRoas = Number((revenueToday / Math.max(1, adSpendToday)).toFixed(2));
  const estimatedMer = Number(((revenueToday * 1.05) / Math.max(1, adSpendToday)).toFixed(2)); // slight offset for MER
  
  // 2. Adjust revenueByChannel share and revenue
  const revenueByChannel = mockDashboardData.revenueByChannel.map((ch) => {
    let extra = 0;
    if (addedRevenue > 0) {
      if (ch.platform === 'haravan' && rand > 0.5) extra = addedRevenue;
      else if (ch.platform === 'shopee' && rand > 0.2) extra = addedRevenue;
      else extra = addedRevenue;
    }
    const newRev = ch.revenue + extra;
    return {
      ...ch,
      revenue: newRev,
      share: Number(((newRev / revenueToday) * 100).toFixed(1)),
    };
  });

  // 3. Adjust adCostBreakdown
  const adCostBreakdown = mockDashboardData.adCostBreakdown.map((ad) => {
    let extraSpend = 0;
    if (ad.platform === 'facebook-ads' && rand > 0.5) extraSpend = addedAdSpend;
    else if (ad.platform === 'tiktok-ads' && rand > 0.2) extraSpend = addedAdSpend;
    else extraSpend = addedAdSpend;
    const newSpend = ad.spend + extraSpend;
    return {
      ...ad,
      spend: newSpend,
      share: Number(((newSpend / adSpendToday) * 100).toFixed(1)),
    };
  });

  // 4. Update hourly revenue
  const currentHourString = new Date().getHours().toString().padStart(2, '0') + ':00';
  const hourlyRevenue = mockDashboardData.hourlyRevenue.map((hr) => {
    // If this represents our current hour segment, add the new revenue
    if (hr.hour === currentHourString || (hr.hour === '24:00' && currentHourString === '00:00')) {
      return {
        ...hr,
        todayRevenue: hr.todayRevenue + addedRevenue,
      };
    }
    return hr;
  });

  // 5. Update realtimeOrders
  const nextOrders = [...mockDashboardData.realtimeOrders];
  if (addedOrders > 0) {
    const nextCode = `#1000${459 + Math.floor(Math.random() * 1000)}`;
    const platforms = ['haravan', 'shopee', 'tiktok-shop'] as const;
    const selectedPlatform = platforms[Math.floor(Math.random() * platforms.length)];
    const names = ['Nguyễn Thị Hằng', 'Phạm Minh Tuấn', 'Hoàng Lê Giang', 'Vũ Khánh Linh'];
    const selectedName = names[Math.floor(Math.random() * names.length)];
    
    nextOrders.unshift({
      id: String(Date.now()),
      createdAt: new Date().toISOString(),
      orderCode: nextCode,
      customerDisplayName: selectedName,
      platform: selectedPlatform,
      orderValue: addedRevenue || 250000,
    });
    
    if (nextOrders.length > 5) {
      nextOrders.pop();
    }
  }

  // 6. Update top products
  const topProducts = mockDashboardData.topProducts.map((p, index) => {
    if (index === 0 && addedOrders > 0 && rand > 0.6) {
      return { ...p, orders: p.orders + 1, revenue: p.revenue + (addedRevenue || 199000) };
    }
    return p;
  });

  const facebookAdsSpend = adCostBreakdown.find(ad => ad.platform === 'facebook-ads')?.spend || 2450000;
  const tiktokAdsSpend = adCostBreakdown.find(ad => ad.platform === 'tiktok-ads')?.spend || 2180000;
  const shopeeAdsSpend = adCostBreakdown.find(ad => ad.platform === 'shopee-ads')?.spend || 990000;

  return {
    ...mockDashboardData,
    revenueToday,
    ordersToday,
    ordersLastHour,
    averageOrderValue,
    adSpendToday,
    estimatedRoas,
    estimatedMer,
    facebookAdsSpend,
    tiktokAdsSpend,
    shopeeAdsSpend,
    revenueByChannel,
    adCostBreakdown,
    hourlyRevenue,
    realtimeOrders: nextOrders,
    topProducts,
    lastUpdatedAt: new Date().toISOString(),
  };
}
```

---

### File: `apps/dashboard/src/index.css`

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

/* Webkit scrollbar customizations for sleek dark look */
::-webkit-scrollbar {
  width: 4px;
  height: 4px;
}

::-webkit-scrollbar-track {
  background: rgba(15, 23, 42, 0.3);
}

::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.2);
  border-radius: 20px;
}

::-webkit-scrollbar-thumb:hover {
  background: rgba(148, 163, 184, 0.4);
}

/* Recharts customization overrides */
.recharts-tooltip-wrapper {
  outline: none !important;
}

/* Custom animation keyframes if needed */
@keyframes pulse-slow {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

.animate-pulse-slow {
  animation: pulse-slow 3s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}
```

---

### File: `apps/dashboard/src/main.tsx`

```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
```

---

### File: `apps/dashboard/src/types/dashboard.ts`

```typescript
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

export interface PlatformHealthDetails {
  status: 'ok' | 'warning' | 'error' | 'unknown';
  label: string;
  lastSuccessAt?: string | null;
  lastErrorAt?: string | null;
}

export interface DashboardData {
  revenueToday: number;
  ordersToday: number;
  ordersLastHour: number;
  averageOrderValue: number;
  adSpendToday: number;
  estimatedRoas: number;
  estimatedMer: number;
  cancelRate: number;
  returnOrdersToday: number;

  facebookAdsSpend: number;
  tiktokAdsSpend: number;
  shopeeAdsSpend: number;

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
  topAdCampaigns: TopAdCampaign[];

  platformHealth: {
    haravan: PlatformHealthDetails;
    shopee: PlatformHealthDetails;
    tiktokShop: PlatformHealthDetails;
    tiktokAds: PlatformHealthDetails;
    metaAds: PlatformHealthDetails;
  };

  lastUpdatedAt: string;
}
```

---

### File: `apps/dashboard/src/utils/formatCurrency.ts`

```typescript
export function formatVND(value: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND'
  }).format(value);
}
```

---

### File: `apps/dashboard/src/utils/formatNumber.ts`

```typescript
export function formatNumber(value: number): string {
  return new Intl.NumberFormat('vi-VN').format(value);
}
```

---

### File: `apps/dashboard/src/utils/time.ts`

```typescript
export function formatTime(dateStr: string): string {
  const d = new Date(dateStr);
  return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export function formatDate(date: Date): string {
  return date.toLocaleDateString('vi-VN', {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  });
}

export function getMinutesDifference(isoString: string): number {
  const diffMs = Date.now() - new Date(isoString).getTime();
  return Math.floor(diffMs / (60 * 1000));
}
```

---

### File: `apps/dashboard/src/vite-env.d.ts`

```typescript
/// <reference types="vite/client" />
```

---

### File: `apps/worker/src/index.ts`

```typescript
export interface Env {
  SUPABASE_URL: string;
  SUPABASE_SERVICE_ROLE_KEY: string;
  BACKEND_URL: string;
  CRON_SECRET: string;
}

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname;

    // Check preflight OPTIONS request
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
          'Access-Control-Allow-Headers': 'Content-Type, Authorization',
        },
      });
    }

    // Router for Webhooks
    if (path.startsWith('/webhooks/')) {
      if (request.method !== 'POST') {
        return new Response(JSON.stringify({ error: 'Method not allowed' }), {
          status: 405,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      const platform = path.split('/')[2];
      const validPlatforms = ['haravan', 'shopee', 'tiktok-shop', 'meta', 'tiktok-ads'];

      if (!validPlatforms.includes(platform)) {
        return new Response(JSON.stringify({ error: 'Unsupported platform webhook' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      try {
        const textPayload = await request.text();
        let payload: any;
        try {
          payload = JSON.parse(textPayload);
        } catch {
          payload = { raw_body: textPayload };
        }

        // Extract event info based on platform
        let eventId = crypto.randomUUID();
        let eventType = 'unknown';
        let platformObjectId = 'unknown';

        if (platform === 'haravan') {
          eventType = request.headers.get('x-haravan-topic') || 'order.update';
          eventId = request.headers.get('x-haravan-event-id') || String(payload.id || eventId);
          platformObjectId = String(payload.id || 'unknown');
        } else if (platform === 'shopee') {
          eventType = payload.code || 'order.status_update';
          eventId = String(payload.order_sn || payload.data?.order_sn || eventId);
          platformObjectId = eventId;
        } else if (platform === 'tiktok-shop') {
          eventType = payload.type || 'order.status_update';
          eventId = payload.event_id || String(payload.data?.order_id || eventId);
          platformObjectId = String(payload.data?.order_id || 'unknown');
        }

        // Insert directly into Supabase via REST API for speed/lightweight footprint
        const dbUrl = `${env.SUPABASE_URL}/rest/v1/webhook_events`;
        const dbResponse = await fetch(dbUrl, {
          method: 'POST',
          headers: {
            'apikey': env.SUPABASE_SERVICE_ROLE_KEY,
            'Authorization': `Bearer ${env.SUPABASE_SERVICE_ROLE_KEY}`,
            'Content-Type': 'application/json',
            'Prefer': 'return=minimal',
          },
          body: JSON.stringify({
            platform: platform === 'meta' ? 'meta-ads' : platform,
            event_type: eventType,
            event_id: eventId,
            platform_object_id: platformObjectId,
            status: 'pending',
            payload: payload,
            received_at: new Date().toISOString(),
          }),
        });

        if (!dbResponse.ok) {
          const dbErr = await dbResponse.text();
          console.error(`Supabase DB Write failed: ${dbResponse.status} - ${dbErr}`);
          // Fallback response for webhook provider to retry, but prevent crashing
          return new Response(JSON.stringify({ error: 'DB Save Failed', details: dbErr }), {
            status: 500,
            headers: {
              'Content-Type': 'application/json',
              'Access-Control-Allow-Origin': '*',
            },
          });
        }

        return new Response(JSON.stringify({ ok: true, event_id: eventId }), {
          status: 200,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
          },
        });
      } catch (err: any) {
        console.error('Webhook error:', err);
        return new Response(JSON.stringify({ error: err.message }), {
          status: 500,
          headers: {
            'Content-Type': 'application/json',
            'Access-Control-Allow-Origin': '*',
          },
        });
      }
    }

    // Default route
    return new Response(JSON.stringify({ service: 'mdata-worker', ok: true }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  },

  // Cron schedule dispatcher
  async scheduled(event: { cron: string }, env: Env, ctx: ExecutionContext): Promise<void> {
    console.log(`[Worker Cron] Fired cron trigger: ${event.cron}`);

    const callBackend = async (path: string, method: string = 'POST') => {
      const url = `${env.BACKEND_URL}${path}`;
      try {
        console.log(`[Worker Cron] Calling ${method} ${url}`);
        const res = await fetch(url, {
          method: method,
          headers: {
            'x-cron-secret': env.CRON_SECRET,
            'Content-Type': 'application/json',
          },
        });
        console.log(`[Worker Cron] Result ${url} -> HTTP ${res.status}`);
      } catch (err) {
        console.error(`[Worker Cron] Failed to call ${url}:`, err);
      }
    };

    // Dispatch tasks according to the trigger pattern
    const cron = event.cron;

    // 1. Health check / keep alive (Render sleep prevention, runs every 10 min)
    if (cron.includes('10') || cron === '*/10 * * * *') {
      ctx.waitUntil(callBackend('/health', 'GET'));
    }

    // 2. Webhook processing & rebuilding summary (runs every 5 min)
    if (cron.includes('5') || cron === '*/5 * * * *') {
      ctx.waitUntil(callBackend('/jobs/process-webhook-events'));
      ctx.waitUntil(callBackend('/jobs/rebuild-summary'));
    }

    // 3. Ecommerce sync (runs every 15 min)
    if (cron.includes('15') || cron === '*/15 * * * *') {
      ctx.waitUntil(callBackend('/jobs/sync/haravan/orders'));
      ctx.waitUntil(callBackend('/jobs/sync/shopee/orders'));
      ctx.waitUntil(callBackend('/jobs/sync/tiktok-shop/orders'));
    }

    // 4. Advertising campaigns sync (runs every 60 min)
    if (cron.includes('0') || cron === '0 * * * *') {
      ctx.waitUntil(callBackend('/jobs/sync/meta-ads'));
      ctx.waitUntil(callBackend('/jobs/sync/tiktok-ads'));
    }

    // 5. Daily database data retention cleanup (runs daily at 02:00 UTC)
    if (cron === '0 2 * * *') {
      ctx.waitUntil(callBackend('/jobs/cleanup'));
    }
  },
};
```

---

### File: `packages/shared/src/constants/index.ts`

```typescript
export const PLATFORMS = {
  HARAVAN: 'haravan',
  SHOPEE: 'shopee',
  TIKTOK_SHOP: 'tiktok-shop',
  META_ADS: 'meta-ads',
  TIKTOK_ADS: 'tiktok-ads',
} as const;

export const SYNC_STATUS = {
  SUCCESS: 'success',
  FAILED: 'failed',
  RUNNING: 'running',
} as const;

export const WEBHOOK_STATUS = {
  PENDING: 'pending',
  PROCESSING: 'processing',
  PROCESSED: 'processed',
  FAILED: 'failed',
} as const;
```

---

### File: `packages/shared/src/index.ts`

```typescript
export * from './types';
export * from './normalizers';
export * from './constants';
```

---

### File: `packages/shared/src/normalizers/index.ts`

```typescript
import { PlatformName } from '../types';

export function normalizeOrderStatus(
  platform: PlatformName,
  platformStatus: string
): 'pending' | 'processing' | 'completed' | 'cancelled' | 'refunded' | 'unknown' {
  const status = platformStatus.toUpperCase().trim();

  switch (platform) {
    case 'haravan':
      // Haravan status mapping (often uses financial_status or fulfillment_status)
      if (['CANCELLED', 'VOIDED'].includes(status)) return 'cancelled';
      if (['REFUNDED'].includes(status)) return 'refunded';
      if (['PAID'].includes(status)) return 'completed';
      if (['PENDING', 'AUTHORIZED'].includes(status)) return 'pending';
      if (['PARTIALLY_PAID', 'PARTIALLY_REFUNDED'].includes(status)) return 'processing';
      return 'processing'; // default for unfulfilled / open orders

    case 'shopee':
      // Shopee order statuses
      if (status === 'UNPAID') return 'pending';
      if (['READY_TO_SHIP', 'PROCESSED', 'SHIPPED'].includes(status)) return 'processing';
      if (status === 'COMPLETED') return 'completed';
      if (status === 'CANCELLED') return 'cancelled';
      if (['TO_RETURN', 'RETURN'].includes(status)) return 'refunded';
      return 'unknown';

    case 'tiktok-shop':
      // TikTok Shop order statuses
      if (status === 'UNPAID') return 'pending';
      if (['AWAITING_SHIPMENT', 'AWAITING_COLLECTION', 'PARTIALLY_SHIPPED', 'SHIPPED', 'DELIVERED'].includes(status)) {
        return 'processing';
      }
      if (status === 'COMPLETED') return 'completed';
      if (status === 'CANCELLED') return 'cancelled';
      return 'unknown';

    default:
      return 'unknown';
  }
}
```

---

### File: `packages/shared/src/types/index.ts`

```typescript
export type PlatformName = 'haravan' | 'shopee' | 'tiktok-shop' | 'meta-ads' | 'tiktok-ads';

export interface PlatformConnection {
  id: string;
  platform: PlatformName;
  shop_id?: string;
  shop_name?: string;
  status: 'active' | 'inactive' | 'error';
  last_connected_at?: string;
  last_successful_sync_at?: string;
  last_error_at?: string;
  last_error_message?: string;
  created_at: string;
  updated_at: string;
}

export interface PlatformToken {
  id: string;
  connection_id: string;
  access_token: string;
  refresh_token?: string;
  expires_at?: string;
  scopes?: string[];
  created_at: string;
  updated_at: string;
}

export interface Order {
  id: string;
  platform: PlatformName;
  platform_order_id: string;
  connection_id?: string;
  status: string;
  normalized_status: 'pending' | 'processing' | 'completed' | 'cancelled' | 'refunded' | 'unknown';
  gross_revenue: number;
  net_revenue: number;
  discount_amount: number;
  shipping_fee: number;
  currency: string;
  customer_name?: string;
  created_at_platform: string;
  updated_at_platform: string;
  raw_data?: any;
  created_at: string;
  updated_at: string;
}

export interface OrderItem {
  id: string;
  order_id: string;
  platform_product_id?: string;
  platform_sku_id?: string;
  sku?: string;
  product_name?: string;
  quantity: number;
  unit_price: number;
  total_price: number;
  raw_data?: any;
  created_at: string;
}

export interface AdInsightsHourly {
  id: string;
  platform: 'meta-ads' | 'tiktok-ads';
  ad_account_id?: string;
  campaign_id?: string;
  campaign_name?: string;
  hour: string;
  spend: number;
  impressions: number;
  clicks: number;
  reach: number;
  cpc: number;
  cpm: number;
  ctr: number;
  conversions: number;
  raw_data?: any;
  created_at: string;
}

export interface HourlyMetrics {
  id: string;
  platform: string;
  hour: string;
  order_count: number;
  gross_revenue: number;
  net_revenue: number;
  cancelled_count: number;
  refund_count: number;
  ad_spend: number;
  roas: number;
  created_at: string;
  updated_at: string;
}

export interface DailyMetrics {
  id: string;
  platform: string;
  date: string;
  order_count: number;
  gross_revenue: number;
  net_revenue: number;
  cancelled_count: number;
  refund_count: number;
  ad_spend: number;
  roas: number;
  created_at: string;
  updated_at: string;
}

export interface WebhookEvent {
  id: string;
  platform: PlatformName;
  event_type?: string;
  event_id?: string;
  platform_object_id?: string;
  status: 'pending' | 'processing' | 'processed' | 'failed';
  payload: any;
  received_at: string;
  processed_at?: string;
  error_message?: string;
}

export interface SyncLog {
  id: string;
  platform: PlatformName;
  job_name: string;
  status: 'success' | 'failed' | 'running';
  started_at: string;
  finished_at?: string;
  records_processed: number;
  error_message?: string;
  metadata?: any;
}

export interface PlatformHealth {
  platform: string;
  status: 'ok' | 'warning' | 'error' | 'unknown';
  last_success_at?: string;
  last_error_at?: string;
  last_error_message?: string;
  latency_ms?: number;
  updated_at: string;
}

export interface LiveDashboardData {
  summary: {
    totalRevenueToday: number;
    totalOrdersToday: number;
    adSpendToday: number;
    roasToday: number;
    orderCountChangePercent?: number;
    revenueChangePercent?: number;
  };
  platformBreakdown: Record<string, {
    revenue: number;
    orders: number;
    spend: number;
    roas: number;
  }>;
  hourlyTrends: {
    hour: string; // ISO String or Format HH:00
    revenue: number;
    orders: number;
  }[];
  topProducts: {
    sku?: string;
    product_name?: string;
    quantity: number;
    revenue: number;
  }[];
  lowStockAlerts: {
    sku: string;
    product_name: string;
    stock: number;
    platform: string;
  }[];
  platformHealth: Record<string, PlatformHealth>;
  lastUpdatedAt: string;
}
```

---

### File: `supabase/migrations/20260625000000_init.sql`

```sql
-- 1. Create enum/type helpers if necessary (standard postgres types are used for compatibility)

-- 2. platform_connections
create table if not exists platform_connections (
  id uuid primary key default gen_random_uuid(),
  platform text not null,
  shop_id text,
  shop_name text,
  status text not null default 'active',
  last_connected_at timestamptz,
  last_successful_sync_at timestamptz,
  last_error_at timestamptz,
  last_error_message text,
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);

-- 3. platform_tokens
create table if not exists platform_tokens (
  id uuid primary key default gen_random_uuid(),
  connection_id uuid references platform_connections(id) on delete cascade,
  access_token text not null,
  refresh_token text,
  expires_at timestamptz,
  scopes text[],
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);

-- 4. orders
create table if not exists orders (
  id uuid primary key default gen_random_uuid(),
  platform text not null,
  platform_order_id text not null,
  connection_id uuid references platform_connections(id) on delete set null,
  status text not null,
  normalized_status text not null,
  gross_revenue numeric default 0,
  net_revenue numeric default 0,
  discount_amount numeric default 0,
  shipping_fee numeric default 0,
  currency text default 'VND',
  customer_name text,
  created_at_platform timestamptz,
  updated_at_platform timestamptz,
  raw_data jsonb,
  created_at timestamptz default now(),
  updated_at timestamptz default now(),
  unique(platform, platform_order_id)
);

-- 5. order_items
create table if not exists order_items (
  id uuid primary key default gen_random_uuid(),
  order_id uuid references orders(id) on delete cascade,
  platform_product_id text,
  platform_sku_id text,
  sku text,
  product_name text,
  quantity int default 0,
  unit_price numeric default 0,
  total_price numeric default 0,
  raw_data jsonb,
  created_at timestamptz default now()
);

-- 6. ad_insights_hourly
create table if not exists ad_insights_hourly (
  id uuid primary key default gen_random_uuid(),
  platform text not null,
  ad_account_id text,
  campaign_id text,
  campaign_name text,
  hour timestamptz not null,
  spend numeric default 0,
  impressions int default 0,
  clicks int default 0,
  reach int default 0,
  cpc numeric default 0,
  cpm numeric default 0,
  ctr numeric default 0,
  conversions numeric default 0,
  raw_data jsonb,
  created_at timestamptz default now(),
  unique(platform, ad_account_id, campaign_id, hour)
);

-- 7. hourly_metrics
create table if not exists hourly_metrics (
  id uuid primary key default gen_random_uuid(),
  platform text not null,
  hour timestamptz not null,
  order_count int default 0,
  gross_revenue numeric default 0,
  net_revenue numeric default 0,
  cancelled_count int default 0,
  refund_count int default 0,
  ad_spend numeric default 0,
  roas numeric default 0,
  created_at timestamptz default now(),
  updated_at timestamptz default now(),
  unique(platform, hour)
);

-- 8. daily_metrics
create table if not exists daily_metrics (
  id uuid primary key default gen_random_uuid(),
  platform text not null,
  date date not null,
  order_count int default 0,
  gross_revenue numeric default 0,
  net_revenue numeric default 0,
  cancelled_count int default 0,
  refund_count int default 0,
  ad_spend numeric default 0,
  roas numeric default 0,
  created_at timestamptz default now(),
  updated_at timestamptz default now(),
  unique(platform, date)
);

-- 9. webhook_events
create table if not exists webhook_events (
  id uuid primary key default gen_random_uuid(),
  platform text not null,
  event_type text,
  event_id text,
  platform_object_id text,
  status text default 'pending',
  payload jsonb not null,
  received_at timestamptz default now(),
  processed_at timestamptz,
  error_message text,
  unique(platform, event_id)
);

-- 10. sync_logs
create table if not exists sync_logs (
  id uuid primary key default gen_random_uuid(),
  platform text not null,
  job_name text not null,
  status text not null,
  started_at timestamptz default now(),
  finished_at timestamptz,
  records_processed int default 0,
  error_message text,
  metadata jsonb
);

-- 11. platform_health
create table if not exists platform_health (
  platform text primary key,
  status text not null default 'unknown',
  last_success_at timestamptz,
  last_error_at timestamptz,
  last_error_message text,
  latency_ms int,
  updated_at timestamptz default now()
);

-- 12. Create Indexes for query optimizations
create index if not exists idx_orders_platform_id on orders(platform, platform_order_id);
create index if not exists idx_orders_created_at_platform on orders(created_at_platform);
create index if not exists idx_webhook_events_status_received on webhook_events(status, received_at);
create index if not exists idx_hourly_metrics_hour on hourly_metrics(hour);
create index if not exists idx_daily_metrics_date on daily_metrics(date);
create index if not exists idx_sync_logs_platform_job_started on sync_logs(platform, job_name, started_at);
create index if not exists idx_order_items_order_id on order_items(order_id);
create index if not exists idx_ad_insights_hourly_hour on ad_insights_hourly(hour);
```

---

