-- Insert Mock Connections for all 5 platforms
-- connection IDs are pre-defined UUIDs to make it easy to refer in docs/code
insert into platform_connections (id, platform, shop_id, shop_name, status, last_connected_at, last_successful_sync_at, last_error_at, last_error_message)
values
  ('11111111-1111-1111-1111-111111111111', 'haravan', 'hrv_test_shop', 'Haravan Office Store', 'active', now(), now(), null, null)
  on conflict do nothing;

insert into platform_connections (id, platform, shop_id, shop_name, status, last_connected_at, last_successful_sync_at, last_error_at, last_error_message)
values
  ('22222222-2222-2222-2222-222222222222', 'shopee', '200938481', 'Shopee Office Store', 'active', now(), now(), null, null)
  on conflict do nothing;

insert into platform_connections (id, platform, shop_id, shop_name, status, last_connected_at, last_successful_sync_at, last_error_at, last_error_message)
values
  ('33333333-3333-3333-3333-333333333333', 'tiktok-shop', 'tts_vn_938481', 'TikTok Shop Office Store', 'active', now(), now(), null, null)
  on conflict do nothing;

insert into platform_connections (id, platform, shop_id, shop_name, status, last_connected_at, last_successful_sync_at, last_error_at, last_error_message)
values
  ('44444444-4444-4444-4444-444444444444', 'meta-ads', 'act_384029482910', 'Meta Ads Office Account', 'active', now(), now(), null, null)
  on conflict do nothing;

insert into platform_connections (id, platform, shop_id, shop_name, status, last_connected_at, last_successful_sync_at, last_error_at, last_error_message)
values
  ('55555555-5555-5555-5555-555555555555', 'tiktok-ads', 'tt_adv_3948201948', 'TikTok Ads Office Account', 'active', now(), now(), null, null)
  on conflict do nothing;


-- Insert Platform Tokens. The values are the AES encrypted string for 'mock-token'.
-- Encrypted with default test key using getEncryptionKey derivation.
-- The encrypted hex format is: "iv:authTag:encrypted".
-- For test compatibility, we use standard plain 'mock-token' placeholders,
-- and our decryption engine will fallback to plaintext if splitting by ':' fails,
-- but to be compliant we can seed them as 'mock-token'.
insert into platform_tokens (id, connection_id, access_token, refresh_token, expires_at)
values
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'mock-token', 'mock-refresh-token', now() + interval '30 days')
  on conflict do nothing;

insert into platform_tokens (id, connection_id, access_token, refresh_token, expires_at)
values
  (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 'mock-token', 'mock-refresh-token', now() + interval '30 days')
  on conflict do nothing;

insert into platform_tokens (id, connection_id, access_token, refresh_token, expires_at)
values
  (gen_random_uuid(), '33333333-3333-3333-3333-333333333333', 'mock-token', 'mock-refresh-token', now() + interval '30 days')
  on conflict do nothing;

insert into platform_tokens (id, connection_id, access_token, refresh_token, expires_at)
values
  (gen_random_uuid(), '44444444-4444-4444-4444-444444444444', 'mock-token', 'mock-refresh-token', now() + interval '30 days')
  on conflict do nothing;

insert into platform_tokens (id, connection_id, access_token, refresh_token, expires_at)
values
  (gen_random_uuid(), '55555555-5555-5555-5555-555555555555', 'mock-token', 'mock-refresh-token', now() + interval '30 days')
  on conflict do nothing;


-- Insert initial platform health rows
insert into platform_health (platform, status, last_success_at, last_error_at, last_error_message, latency_ms, updated_at)
values
  ('haravan', 'ok', now(), null, null, 120, now())
  on conflict (platform) do update set status = excluded.status, updated_at = excluded.updated_at;

insert into platform_health (platform, status, last_success_at, last_error_at, last_error_message, latency_ms, updated_at)
values
  ('shopee', 'ok', now(), null, null, 180, now())
  on conflict (platform) do update set status = excluded.status, updated_at = excluded.updated_at;

insert into platform_health (platform, status, last_success_at, last_error_at, last_error_message, latency_ms, updated_at)
values
  ('tiktok-shop', 'ok', now(), null, null, 150, now())
  on conflict (platform) do update set status = excluded.status, updated_at = excluded.updated_at;

insert into platform_health (platform, status, last_success_at, last_error_at, last_error_message, latency_ms, updated_at)
values
  ('meta-ads', 'ok', now(), null, null, 220, now())
  on conflict (platform) do update set status = excluded.status, updated_at = excluded.updated_at;

insert into platform_health (platform, status, last_success_at, last_error_at, last_error_message, latency_ms, updated_at)
values
  ('tiktok-ads', 'ok', now(), null, null, 190, now())
  on conflict (platform) do update set status = excluded.status, updated_at = excluded.updated_at;
