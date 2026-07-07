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
  shop_id text,
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
  unique(platform, shop_id, hour)
);

-- 8. daily_metrics
create table if not exists daily_metrics (
  id uuid primary key default gen_random_uuid(),
  platform text not null,
  shop_id text,
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
  unique(platform, shop_id, date)
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
