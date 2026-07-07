# Free-Tier Retention & Execution Strategy

MData achieves a $0/month budget by integrating free tiers from Cloudflare, Render, and Supabase. To remain inside free usage limits forever with zero manual intervention, we implement the following automation:

## 1. Storage Retention Limits (Supabase Free PostgreSQL)

Supabase Free limits database size to **500MB**. Without cleanups, logs and raw webhooks will exhaust this quota within months. MData enforces strict data retention jobs:

| Table Name | Raw Data Retention | Aggregated Duration | Retention Policy |
| :--- | :--- | :--- | :--- |
| `webhook_events` | 14 Days | N/A | Purged daily by `/jobs/cleanup` |
| `sync_logs` | 30 Days | N/A | Purged daily by `/jobs/cleanup` |
| `orders` | 90 Days (raw_data) | Forever (summary stats) | Nullify the `raw_data` jsonb column after 90 days, keeping the order row |
| `ad_insights_hourly` | 90 Days | 24 Months | Truncated down to daily/hourly aggregates |
| `hourly_metrics` | N/A | 12 Months | Preserved for YoY trend comparisons |
| `daily_metrics` | N/A | Forever | Compact summary rows (5 channels x 365 days = ~1,800 rows/year) |

## 2. Server Wake Optimization (Render Free Web Service)

Render Free instances automatically sleep after **15 minutes** of inactivity. The first request after sleep takes 60-90 seconds to compile and return (cold boot).
- **Anti-Sleep Cron**: A Cloudflare scheduled trigger calls `GET /health` on the backend every 10 minutes.
- **Fail-Safe Polling**: If Render is restarting or asleep, the Dashboard client logs a warning banner but displays the previously loaded state.

## 3. Worker Limits (Cloudflare Workers Free)

Cloudflare Workers Free provides **100,000 requests per day** (resets daily at 00:00 UTC).
- **Decoupled Webhooks**: Webhooks from Shopee, TikTok Shop, and Haravan write straight to Supabase REST endpoints instead of calling Render. This is lightweight and takes < 10ms of CPU time, avoiding limits.
- **Batch Processing**: Webhooks are batch-processed asynchronously by Render in groups of 50 via `/jobs/process-webhook-events`.
