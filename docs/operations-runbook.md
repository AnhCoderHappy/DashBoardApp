# Operations Runbook

This runbook describes the diagnostic and recovery procedures for MData dashboard operations.

## 1. Monitoring Sync Latency & Errors

MData writes telemetry logs directly to `sync_logs` and connections status to `platform_health` in Supabase.

To review platform health directly:
```sql
SELECT platform, status, last_success_at, last_error_message, latency_ms 
FROM platform_health;
```

To view the last 20 execution failures:
```sql
SELECT platform, job_name, started_at, error_message, metadata 
FROM sync_logs 
WHERE status = 'failed' 
ORDER BY started_at DESC 
LIMIT 20;
```

## 2. Triggering Manual Syncs

If a platform's health status is in an error state or data is stale, you can trigger manual refreshes by calling the protected `/jobs` endpoints using a tool like Postman or `curl`.

### Rebuild all daily/hourly caches:
```bash
curl -X POST \
  -H "x-cron-secret: <YOUR_CRON_SECRET>" \
  "https://your-backend.onrender.com/jobs/rebuild-summary"
```

### Sync Haravan Orders:
```bash
curl -X POST \
  -H "x-cron-secret: <YOUR_CRON_SECRET>" \
  "https://your-backend.onrender.com/jobs/sync/haravan/orders"
```

### Trigger database cleanup manually:
```bash
curl -X POST \
  -H "x-cron-secret: <YOUR_CRON_SECRET>" \
  "https://your-backend.onrender.com/jobs/cleanup"
```

## 3. Resolving Token Expiration Failures

If you receive a Telegram alert saying `Shopee Token Refresh Failed` or similar:
1. Check the Shopee/TikTok Shop developer portal to see if your authorization has expired (partner tokens usually require re-auth every 1-3 years depending on the platform).
2. Re-authenticate via the platform's OAuth screen to retrieve a new `authorization_code`.
3. Update the `platform_tokens` table with the new `access_token` and `refresh_token`.
