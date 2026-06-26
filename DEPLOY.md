# Going live on free tiers — Supabase + Render

This walks through getting `backend/` actually running on the public internet, talking to a
real Postgres database and real encrypted-blob storage, for $0.

## 1. Create the Supabase project

1. Go to supabase.com → New Project. Pick any region close to where Render will run (matching
   regions cuts latency a bit, not required).
2. Wait for provisioning (~2 min).
3. **Database → SQL Editor** → paste the contents of `backend/sql/schema.sql` → Run.
   This creates `users`, `sessions`, `messages`.
4. **Project Settings → Database** → copy the **Connection string (URI)**, "Transaction"
   pooler mode, port `6543`. This is your `DATABASE_URL`.
5. **Project Settings → API** → copy:
   - `Project URL` → `SUPABASE_URL`
   - `service_role` secret key → `SUPABASE_SERVICE_ROLE_KEY` (⚠️ this key bypasses RLS —
     never put it in the Android app, only in Render's server env)
6. **Storage** → New bucket → name it `maunkavach-encrypted-blobs` (or whatever you set
   `SUPABASE_STORAGE_BUCKET` to) → **make it a private bucket** (not public). The bucket will
   only ever contain opaque AES-GCM ciphertext, but private is still the right default.

## 2. Generate your JWT secret

```bash
openssl rand -hex 32
```
Save this — it's `JWT_SECRET`.

## 3. Deploy to Render

**Option A — render.yaml (recommended):**
1. Push this repo (or just the `backend/` folder) to a GitHub repo.
2. Render dashboard → New → Blueprint → point at the repo. Render reads `backend/render.yaml`
   and creates the service.
3. Fill in the env vars it asks for (`DATABASE_URL`, `SUPABASE_URL`,
   `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_STORAGE_BUCKET`) — `JWT_SECRET` auto-generates.

**Option B — manual:**
1. Render dashboard → New → Web Service → connect your repo → set root directory to `backend`.
2. Build command: `npm install`. Start command: `npm start`.
3. Add the same env vars manually under the Environment tab.

Render will give you a URL like `https://maunkavach-backend.onrender.com`.

> Free-tier note: Render's free web services spin down after ~15 min idle and take a few
> seconds to wake on the next request — fine for a demo/early users, not for "instant message
> delivery" expectations. Paid tier removes this once you're ready.

## 4. Point the Android app at it

- `ApiClient.baseUrl = "https://maunkavach-backend.onrender.com"`
- `NativeWebSocketClient` → host = `maunkavach-backend.onrender.com`, port 443, path `/ws`,
  and append `?token=<jwt>` to the path after login. (The hand-rolled native WebSocket client
  in the scaffold is minimal — for production reliability against Render's proxy, consider
  using `androidx.compose` + the platform's `java.net.http` or, since you've already dropped
  the native-only constraint for the backend, you could also just point the Android client at
  a maintained WS client. Flag if you want me to swap that piece too.)
- Generate the real certificate pin for `network_security_config.xml`:
  ```bash
  echo | openssl s_client -connect maunkavach-backend.onrender.com:443 2>/dev/null | \
    openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | \
    openssl dgst -sha256 -binary | openssl enc -base64
  ```

## 5. Verify it's actually live

```bash
curl https://maunkavach-backend.onrender.com/health
curl -X POST https://maunkavach-backend.onrender.com/auth/register \
  -H "Content-Type: application/json" -d '{"username":"alice","password":"testpass123"}'
curl -X POST https://maunkavach-backend.onrender.com/auth/login \
  -H "Content-Type: application/json" -d '{"username":"alice","password":"testpass123"}'
# copy the returned token, then:
curl https://maunkavach-backend.onrender.com/messages \
  -H "Authorization: Bearer <token>"
```

Then check Supabase's **Table Editor → messages** — confirm every row's `encrypted_payload`
is unreadable ciphertext, never plaintext. That's the whole point.

## 6. Later: moving to AWS

When you outgrow free tiers, the migration is mostly mechanical since nothing here is
Render/Supabase-specific in a deep way:
- Postgres: Supabase → Amazon RDS Postgres (same `DATABASE_URL` shape, same `pg` driver).
- Storage: Supabase Storage → S3 directly (swap `storage.js`'s implementation for
  `@aws-sdk/client-s3`; same upload/download/signed-URL shape).
- Compute: Render → ECS/Fargate, Elastic Beanstalk, or a plain EC2 box behind an ALB.
No changes needed in the Android app beyond the new base URL and a new cert pin.

## What's still not done before "real production"

Being direct about gaps, not glossing over them:
- No rate limiting on `/auth/login` or `/auth/register` — add this before going public, or
  someone can brute-force passwords or spam-create accounts. (`express-rate-limit`-style
  logic, or Render/Cloudflare-level rate limiting.)
- No password reset flow.
- No real push-notification wake-up (FCM) — without it, a recipient with the app closed won't
  know a message arrived until they reopen it. Spec explicitly allows FCM as the wake-up-only
  channel; wire it in when ready.
- The crypto layers (`CipherLayer.kt`'s personal-cipher/rotation stages) still haven't been
  reviewed by anyone but us — get a second set of eyes before trusting this with real secrets.
