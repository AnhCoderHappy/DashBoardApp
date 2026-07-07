# MData — $0/Month Marketing & Ecommerce Live Dashboard

MData is an internal office dashboard designed to run 24/7 on an office TV or monitor. It aggregates and displays live sales and ads metrics from **Haravan, Shopee, TikTok Shop, Meta Ads, and TikTok Ads** under a $0/month budget using free-tier services.

## Architecture

- **Frontend**: React + Vite + TypeScript + Recharts deployed to **Cloudflare Pages Free**
- **Backend**: Node.js + Express + TypeScript deployed to **Render Free**
- **Webhook Gateway + Scheduler**: **Cloudflare Workers Free**
- **Database**: **Supabase Free PostgreSQL**
- **Backup Scheduler**: **GitHub Actions** (fallback sleep prevention)

---

## Monorepo Layout

```txt
mdata/
  apps/
    dashboard/          # Vite React Dashboard App
    backend/            # Express Sync & Api Server
    worker/             # Cloudflare Webhook Receiver & Cron Dispatcher
  packages/
    shared/             # Common models, normalizers, and constants
  supabase/
    migrations/         # PostgreSQL Schema migrations
    seed.sql            # Seeding mock platform connections
  docs/                 # Comprehensive design and runbook docs
```

---

## Local Development Setup

### 1. Prerequisites
- Node.js (v18+)
- `pnpm` (v8+)

### 2. Installation
Install all workspace dependencies:
```bash
pnpm install
```

### 3. Environment Configurations
Create `.env` inside `apps/backend/` (see `apps/backend/.env.example` as a template):
```bash
cp apps/backend/.env.example apps/backend/.env
```

### 4. Running the Dev Servers
Start all applications concurrently:
```bash
pnpm dev:shared     # Build shared library first
pnpm dev:backend    # Starts Express Backend (localhost:8080)
pnpm dev:dashboard  # Starts React Dashboard App (localhost:3000)
```

In development mode, `MOCK_PLATFORMS=true` is enabled by default. The connectors generate randomized, realistic simulated orders and ad spend hourly, which will automatically propagate to the dashboard.

---

## Production Deployment Steps

### 1. Supabase Database
1. Create a free project on [Supabase](https://supabase.com/).
2. Go to the SQL Editor in your Supabase Dashboard and run the contents of [20260625000000_init.sql](file:///d:/MData/supabase/migrations/20260625000000_init.sql) to initialize tables.
3. (Optional) Run the SQL contents of [seed.sql](file:///d:/MData/supabase/seed.sql) to populate mock store connections.

### 2. Render Backend
1. Create a Free Web Service on [Render](https://render.com/).
2. Set Build Command: `pnpm install && pnpm --filter backend build`
3. Set Start Command: `pnpm --filter backend start`
4. Set Environment Variables:
   - `SUPABASE_URL`
   - `SUPABASE_SERVICE_ROLE_KEY`
   - `CRON_SECRET` (generate a secure random key)
   - `ENCRYPTION_KEY` (generate a secure 32-byte key)
   - `FRONTEND_ORIGIN` (your Cloudflare Pages URL)
   - `MOCK_PLATFORMS` (`false` for live sync)

### 3. Cloudflare Worker
1. Deploy the worker in `apps/worker` using Wrangler:
   ```bash
   cd apps/worker
   npx wrangler deploy
   ```
2. Configure Wrangler Secrets in the Cloudflare Portal:
   - `SUPABASE_URL`
   - `SUPABASE_SERVICE_ROLE_KEY`
   - `BACKEND_URL` (your Render service url)
   - `CRON_SECRET` (matching Render `CRON_SECRET`)

### 4. Cloudflare Pages Dashboard
1. Connect your GitHub repository to [Cloudflare Pages](https://pages.cloudflare.com/).
2. Build Settings:
   - Framework preset: `Vite`
   - Build command: `pnpm --filter dashboard build`
   - Output directory: `apps/dashboard/dist`
3. Environment Variables:
   - `VITE_API_BASE_URL` (your Render service url)
