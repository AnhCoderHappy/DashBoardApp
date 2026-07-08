CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS source_channel text DEFAULT 'unknown';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS business_time timestamptz;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS business_date date;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS business_hour smallint;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS external_updated_at_platform timestamptz;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS external_version text;

UPDATE orders
SET source_channel = CASE
    WHEN platform IN ('shopee', 'facebook', 'tiktok-shop', 'pos') THEN platform
    WHEN source_channel IS NULL THEN 'unknown'
    ELSE source_channel
END,
platform = CASE
    WHEN platform IN ('shopee', 'facebook', 'tiktok-shop', 'pos') THEN 'pancake'
    ELSE platform
END,
business_time = COALESCE(business_time, created_at_platform),
business_date = COALESCE(business_date, (created_at_platform AT TIME ZONE 'Asia/Ho_Chi_Minh')::date),
business_hour = COALESCE(business_hour, EXTRACT(HOUR FROM created_at_platform AT TIME ZONE 'Asia/Ho_Chi_Minh')::smallint)
WHERE platform IN ('shopee', 'facebook', 'tiktok-shop', 'pos', 'pancake');

CREATE INDEX IF NOT EXISTS idx_orders_connection_business_date ON orders(connection_id, business_date);
CREATE INDEX IF NOT EXISTS idx_orders_connection_source_business_date ON orders(connection_id, source_channel, business_date);
CREATE INDEX IF NOT EXISTS idx_orders_connection_status_business_date ON orders(connection_id, normalized_status, business_date);
CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_connection_platform_order ON orders(connection_id, platform_order_id);

CREATE TABLE IF NOT EXISTS dashboard_snapshots (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id uuid REFERENCES platform_connections(id) ON DELETE CASCADE,
    snapshot_date date NOT NULL,
    snapshot_type text NOT NULL,
    payload jsonb NOT NULL,
    version bigint NOT NULL DEFAULT 1,
    generated_at timestamptz DEFAULT now(),
    last_synced_at timestamptz,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now(),
    UNIQUE(connection_id, snapshot_date, snapshot_type)
);

CREATE TABLE IF NOT EXISTS product_daily_metrics (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id uuid REFERENCES platform_connections(id) ON DELETE CASCADE,
    source_channel text NOT NULL,
    metric_date date NOT NULL,
    platform_product_id text,
    platform_sku_id text,
    sku text,
    product_name text,
    quantity_sold int DEFAULT 0,
    gross_revenue numeric DEFAULT 0,
    net_revenue numeric DEFAULT 0,
    order_count int DEFAULT 0,
    refund_count int DEFAULT 0,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now(),
    UNIQUE(connection_id, metric_date, source_channel, platform_product_id, platform_sku_id)
);

ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS connection_id uuid REFERENCES platform_connections(id);
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS payload_hash text;
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS attempt_count int DEFAULT 0;
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS next_retry_at timestamptz;
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS locked_at timestamptz;
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS locked_by text;
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS event_updated_at_platform timestamptz;

CREATE TABLE IF NOT EXISTS realtime_outbox (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id uuid REFERENCES platform_connections(id) ON DELETE CASCADE,
    event_type text NOT NULL,
    aggregate_type text,
    aggregate_id uuid,
    payload jsonb NOT NULL,
    status text NOT NULL DEFAULT 'pending',
    attempt_count int DEFAULT 0,
    next_retry_at timestamptz,
    published_at timestamptz,
    created_at timestamptz DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dashboard_snapshots_lookup ON dashboard_snapshots(connection_id, snapshot_date, snapshot_type);
CREATE INDEX IF NOT EXISTS idx_product_daily_metrics_revenue ON product_daily_metrics(connection_id, metric_date, gross_revenue DESC);
CREATE INDEX IF NOT EXISTS idx_webhook_events_retry ON webhook_events(status, next_retry_at, received_at);
CREATE INDEX IF NOT EXISTS idx_realtime_outbox_retry ON realtime_outbox(status, next_retry_at, created_at);
