# API Credentials & Token Connection Guide

To populate the MData dashboard with real data, platform tokens must be generated and stored securely. This guide outlines how to hook up each channel.

## 1. Local Encrypted Token Seeding

Tokens are stored encrypted with AES-256-GCM. During development or manual setup, you can seed tokens into the database. In mock mode (`MOCK_PLATFORMS=true`), setting access tokens to `'mock-token'` is sufficient.

To encrypt credentials before manual insert:
```javascript
// Run via a scratch script in apps/backend
import { encryptSecret } from './src/utils/crypto';
console.log(encryptSecret('your-platform-token'));
```

## 2. Platform Authentication Workflows

### Haravan Connection
- Haravan uses standard OAuth 2.0. You must create a Haravan Private App in your Haravan Shop Admin.
- Retrieve the long-lived **Access Token**.
- Add a row in `platform_connections` with `platform: 'haravan'` and `shop_id`.
- Insert the encrypted access token in `platform_tokens` pointing to the connection.

### Shopee API Connection
- Register an application on the **Shopee Open Platform** to obtain your `Partner ID` and `Partner Key`.
- Set up redirect URL pointing to your backend endpoint for authorization callbacks.
- After authorization, Shopee returns an `authorization_code`. The backend uses this code to request the initial `access_token` and `refresh_token`.
- Shopee tokens expire every 4 hours. The MData Cron automatically handles rotation via Shopee's refresh endpoint before each sync.

### TikTok Shop Connection
- Create an app on the **TikTok Shop Partner Partner Portal**.
- Authorize your shop to get the refresh token.
- TikTok Shop tokens expire every 14 days. The backend cron handles refresh requests automatically using `/api/v2/token/refresh`.

### Meta Ads Integration
- Register as a Meta Developer and create an App with the **Marketing API** product enabled.
- Obtain a long-lived **System User Access Token** with `ads_read` and `read_insights` permissions.
- Save this token under `platform_tokens` for the connection ID referencing your Meta Ad Account ID.

### TikTok Ads Integration
- Create a TikTok Marketing Developer Account.
- Generate an **Access Token** via OAuth or Developer Console.
- Save this token under `platform_tokens` referencing your TikTok Advertiser ID.
