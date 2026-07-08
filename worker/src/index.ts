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

      const pathParts = path.split('/');
      const platform = pathParts[2];
      const validPlatforms = ['pancake', 'haravan', 'shopee', 'tiktok-shop', 'meta', 'tiktok-ads'];

      if (!validPlatforms.includes(platform)) {
        return new Response(JSON.stringify({ error: 'Unsupported platform webhook' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        });
      }

      try {
        const textPayload = await request.text();

        if (platform === 'pancake') {
          const shopId = pathParts[3];
          if (!shopId) {
            return new Response(JSON.stringify({ error: 'Missing Pancake shopId' }), {
              status: 400,
              headers: { 'Content-Type': 'application/json' },
            });
          }

          const backendUrl = `${env.BACKEND_URL}/api/webhooks/pancake/${encodeURIComponent(shopId)}`;
          const backendResponse = await fetch(backendUrl, {
            method: 'POST',
            headers: {
              'Content-Type': request.headers.get('Content-Type') || 'application/json',
            },
            body: textPayload,
          });

          return new Response(await backendResponse.text(), {
            status: backendResponse.status,
            headers: {
              'Content-Type': backendResponse.headers.get('Content-Type') || 'application/json',
              'Access-Control-Allow-Origin': '*',
            },
          });
        }

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
