# Deploying splitfy

How to run the API in staging and production, ship the mobile apps, and keep it
all observable. Nothing here assumes a particular hosting vendor: the API is a
single Docker image configured entirely by environment variables, and the
[hosting options](#hosting-options) section compares a few places to run it.

- [What gets deployed](#what-gets-deployed)
- [PostgreSQL](#postgresql)
- [Environment variables](#environment-variables)
- [Running migrations](#running-migrations)
- [Domains and TLS](#domains-and-tls)
- [Health checks and shutdown](#health-checks-and-shutdown)
- [Logs, errors and metrics](#logs-errors-and-metrics)
- [Releasing the backend](#releasing-the-backend)
- [Android release signing](#android-release-signing)
- [iOS release](#ios-release)
- [Hosting options](#hosting-options)
- [What you need to provide](#what-you-need-to-provide)

## What gets deployed

| Piece | Built by | Lives at |
|---|---|---|
| API image | `.github/workflows/release-backend.yml` on a `v*` tag | `ghcr.io/<owner>/splitfy-api:<tag>` |
| Android App Bundle | `.github/workflows/release-android.yml` on a `v*` tag | workflow artifact `splitfy-release-aab` → Play Console |
| iOS build | Xcode (Archive) for now | App Store Connect → TestFlight |

A deploy is always: **build the image → run migrations → roll out new instances →
the platform waits for `/ready` before sending them traffic.**

Run two environments, **staging** and **production**, each with its own database,
secrets and Sentry environment. Never point staging at the production database.

## PostgreSQL

Use a managed PostgreSQL 16 (the version CI and `docker-compose.yml` use). What
matters, whichever provider you choose:

- **Automated daily backups and point-in-time recovery (PITR)**, with a retention
  window you have written down (7 days is a reasonable floor; the Privacy Policy
  quotes this number, so keep them in sync).
- **Practise a restore** before launch: restore to a new instance at a timestamp,
  point a staging API at it, and check the data. A backup you have never restored
  is a hope, not a backup.
- **TLS to the database.** Add `?sslmode=require` (or `verify-full` with the
  provider's CA) to `DATABASE_URL`.
- **Connection limits.** Each API instance opens up to `DATABASE_POOL_SIZE`
  connections (default 10). Instances × pool size must stay below the plan's
  connection limit, with headroom for migrations and a psql session. Serverless
  Postgres with a pooler (Neon, Supabase) works; use its *session* mode or direct
  endpoint, because Flyway's lock needs session semantics.
- **Same region as the API.** Every request makes several round trips.
- Separate databases (ideally separate instances) for staging and production.

Providers that fit: Neon, Supabase, AWS RDS / Aurora, Google Cloud SQL, Azure
Database for PostgreSQL, DigitalOcean Managed Databases, Crunchy Bridge, or the
managed Postgres that comes with Fly.io, Render or Railway. Check each one's PITR
retention on the plan you pick — on several it is a paid-tier feature.

## Environment variables

Everything is read by `backend/src/main/resources/application.yaml`. The server
**refuses to start in `PRODUCTION`** with the development JWT secret, the
development database password, or no `RESEND_API_KEY`. Keep secrets in your
platform's secret store, never in the repository or the image.

**Required in production**

| Variable | Example | Notes |
|---|---|---|
| `APP_ENV` | `PRODUCTION` | The image defaults to `PRODUCTION`; set `STAGING` for staging. |
| `DATABASE_URL` | `jdbc:postgresql://db.example:5432/splitfy?sslmode=require` | JDBC form, not `postgres://`. |
| `DATABASE_USER` / `DATABASE_PASSWORD` | | Secret. |
| `JWT_SECRET` | 64 random characters | Secret. `openssl rand -base64 48`. Rotating it signs everyone out. |
| `JWT_ISSUER` | `https://splitfy.onrender.com` | |
| `JWT_AUDIENCE` | `splitfy-mobile` | |
| `RESEND_API_KEY` | `re_…` | Secret. See `docs/SERVER_README.md`. |
| `TOTP_ENCRYPTION_KEY` | 32+ random characters | Secret. `openssl rand -base64 32`. Encrypts authenticator-app secrets; changing or losing it turns off two-step verification for everyone enrolled. |
| `EMAIL_FROM` | `Splitfy <onboarding@resend.dev>` | Resend's test sender, which only delivers to the Resend account owner. Switch to an address on your own verified domain before real users. |
| `PUBLIC_BASE_URL` | `https://splitfy.onrender.com` | Host that serves invite links; see [Domains](#domains-and-tls). |
| `ANDROID_CERT_SHA256` | `AA:BB:…` | Play **app signing** certificate fingerprint (Play Console → App integrity). |
| `APPLE_APP_IDS` | `ABCDE12345.com.elozino.splitfy` | |

**Optional**

| Variable | Default | Notes |
|---|---|---|
| `PORT` | `8080` | Most platforms inject this. |
| `DATABASE_POOL_SIZE` | `10` | See connection limits above. |
| `DATABASE_RUN_MIGRATIONS` | `true` | Set `false` when you run the [migrator](#running-migrations) as a deploy step. |
| `DATABASE_STATEMENT_TIMEOUT_SECONDS` | `30` | |
| `GOOGLE_CLIENT_IDS` / `APPLE_CLIENT_IDS` | empty | Social sign-in; empty hides the provider. |
| `SENTRY_DSN` | empty | Error reporting; empty turns it off. |
| `APP_VERSION` | image tag | Set by the release workflow; tags Sentry events. |
| `METRICS_TOKEN` | empty | Enables `/metrics` behind this bearer token (≥ 24 chars). |
| `FCM_SERVICE_ACCOUNT_JSON` / `FCM_SERVICE_ACCOUNT_PATH` | empty | Secret. Firebase service-account key (JSON, or a path to it); empty means pushes are only logged. See `docs/PUSH_NOTIFICATIONS.md`. |
| `PAYMENT_REMINDERS_ENABLED` | `true` | Daily reminder for old balances; safe with several instances. |
| `PAYMENT_REMINDER_AFTER_DAYS` / `PAYMENT_REMINDER_EVERY_DAYS` / `PAYMENT_REMINDER_HOUR_UTC` | `3` / `7` / `9` | |
| `LOG_FORMAT` | `json` in the image | `text` for humans. |
| `LOG_LEVEL` / `APP_LOG_LEVEL` | `INFO` / `DEBUG` | Root and `com.elozino.splitfy` levels. Consider `INFO` for the latter in production. |
| `SHUTDOWN_GRACE_MS` / `SHUTDOWN_TIMEOUT_MS` | `5000` / `20000` | See [shutdown](#health-checks-and-shutdown). |
| `JAVA_TOOL_OPTIONS` | heap 75% of the container limit | Append flags rather than replacing them. |
| `FEATURE_WALLET` | `false` | Leave off: there is no payment provider. |
| `BCRYPT_COST` | `12` | |

Give the container at least **512 MB** of memory; 1 GB is comfortable.

## Running migrations

Migrations live in `backend/src/main/resources/db/migration` and are applied by
Flyway. Two ways to run them:

1. **On boot (default).** Every instance runs pending migrations before it starts
   serving. Flyway takes a database lock, so several instances starting at once is
   safe. Simple, and fine for a single instance.
2. **As a deploy step (recommended once you run more than one instance).** Run the
   same image once with the migrator, then roll out with
   `DATABASE_RUN_MIGRATIONS=false`:

   ```bash
   docker run --rm --env-file prod.env ghcr.io/<owner>/splitfy-api:v1.2.0 \
     java -cp /app/app.jar com.elozino.splitfy.backend.MigrateKt
   ```

   It exits non-zero on failure, so a bad migration stops the deploy instead of
   crash-looping every new instance. Most platforms have a slot for this
   ("release command", "pre-deploy command").

Write migrations to be compatible with the code that is already running
(expand/contract): add a column in one release and start using it in the next;
stop using a column in one release and drop it in a later one. During a rolling
deploy, old and new instances share the schema.

Migration numbers are assigned per branch in this repository (V6 receipts, V7
push notifications, V8 account deletion). Merge them in order before the first
production deploy; Flyway rejects an older version number that arrives after a
newer one has been applied.

## Domains and TLS

**Splitfy has no domain of its own yet.** `splitfy.app` belongs to an unrelated
product and must not be used. Until a domain is bought, everything runs on the
Render host, HTTPS only:

| Host | Serves | Configured in |
|---|---|---|
| `splitfy.onrender.com` | The whole API, invite links (`/i/*`) and app-link verification (`/.well-known/assetlinks.json`, `/.well-known/apple-app-site-association`) | `api.baseUrl.release` in `app/androidApp/config.properties`; `API_HOST` in `app/iosApp/Configuration/Release.xcconfig`; the invite host in the Android manifest, the iOS entitlements and `InviteLinks.kt`; `PUBLIC_BASE_URL` and `JWT_ISSUER` on the server |

The backend serves the invite pages and the `.well-known` files itself, so one host
covers both. **When a domain is bought,** change every place in that table in one
release: typically `api.<domain>` for the API and `<domain>` for invite links,
with `/i/*` and `/.well-known/*` routed to the API. Apps already installed keep
calling the old host, so keep the Render URL working until they have updated. The
`.well-known` files must be served directly (no redirect) with a `200`.

The API runs behind the platform's TLS-terminating proxy and trusts
`X-Forwarded-*` outside `LOCAL`. Do not expose it directly to the internet without
one.

## Health checks and shutdown

| Path | Meaning | Use for |
|---|---|---|
| `/health` | the process is up; never touches the database | liveness (restart if failing) |
| `/ready` | the database answers `SELECT 1`; `503` while shutting down | readiness / load-balancer health check |

On `SIGTERM` the server flips `/ready` to `503`, keeps serving for
`SHUTDOWN_GRACE_MS` so the load balancer notices, then gives in-flight requests up
to `SHUTDOWN_TIMEOUT_MS` to finish. Keep the timeout below the platform's kill
deadline (often 30 s). The image runs the JVM as PID 1 with exec form, so the
signal arrives directly.

## Logs, errors and metrics

- **Logs** go to stdout as one JSON object per line (`LOG_FORMAT=json`), each with
  the request's `traceId` in the `mdc` field. That id is also returned to clients
  in `X-Request-Id` and in every error body, so a user's report maps to one
  request. The access log has method, path, status and duration only: no query
  strings (search takes an email there), invite tokens masked, no bodies or
  headers. Ship stdout to whatever log store your platform offers.
- **Errors** go to Sentry when `SENTRY_DSN` is set: unhandled exceptions and any
  5xx. Events are scrubbed of emails, tokens, IP addresses and request data before
  they leave the process. Create separate Sentry projects for the API, Android and
  iOS, and use the environment field to tell staging from production.
- **Metrics** are Prometheus-format at `/metrics`, which answers `404` until
  `METRICS_TOKEN` is set and then requires `Authorization: Bearer <token>`. That
  keeps it safe on platforms that expose only one port. Point Grafana Cloud, a
  self-hosted Prometheus, or your platform's scraper at it with that header.

Alerts worth having from day one: `/ready` failing, 5xx rate above a few percent,
p95 latency above ~1 s, database CPU or connections near the limit, and any new
Sentry issue in production.

## Releasing the backend

```bash
git tag v1.0.0 && git push origin v1.0.0
```

`release-backend.yml` runs the backend tests, then builds `backend/Dockerfile` and
pushes `ghcr.io/<owner>/splitfy-api:v1.0.0` (plus `:sha-<short>`, and `:latest`
for tags without a pre-release suffix such as `-rc1`). It uses the repository's
`GITHUB_TOKEN`; nothing to configure. The first push creates the package as
private: either make it public under the package settings, or give your host a
read-only token (a fine-grained PAT with `read:packages`) to pull it.

Deploying the new image is left to the platform. Most can redeploy on a webhook,
or you can add a deploy step to the workflow once you have chosen one.

Rolling back is redeploying the previous tag. Migrations do not roll back, which
is why they must stay backward compatible.

## Android release signing

Nothing secret is in the repository. Release builds are signed when the build can
find a keystore, from either:

- **CI:** environment variables `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`,
  `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`; or
- **your machine:** `app/androidApp/keystore.properties` (gitignored), copied from
  `keystore.properties.example`.

Without either, release builds are produced unsigned.

One-time setup:

1. Create an **upload key** (Play App Signing keeps the real signing key):
   ```bash
   keytool -genkeypair -v -keystore splitfy-upload.jks -alias upload \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
   Store the file and both passwords in your password manager; losing the upload
   key means a reset request to Google.
2. Add repository secrets (Settings → Secrets and variables → Actions):
   `ANDROID_KEYSTORE_BASE64` (`base64 -i splitfy-upload.jks | pbcopy`),
   `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, and
   optionally `SENTRY_DSN_ANDROID`.
3. Create the app in Play Console, enrol in Play App Signing, upload the first
   bundle to the **internal testing** track by hand.
4. Copy the **app signing** certificate SHA-256 into the server's
   `ANDROID_CERT_SHA256` so invite links verify.

On each `v*` tag, `release-android.yml` builds a signed AAB with
`versionName` from the tag and `versionCode` from the workflow run number, and
attaches it to the run. If the secrets are missing it logs a notice and succeeds
without building. Automating the Play upload (e.g. with a service account and
`r0adkll/upload-google-play`) is a natural next step.

## iOS release

Not automated yet; archive from Xcode and upload to App Store Connect, then
TestFlight. Before the first submission:

- Set `TEAM_ID` (Local.xcconfig or CI), enable **Sign in with Apple** and
  **Associated Domains** (`applinks:splitfy.onrender.com`) for the bundle id.
- Add a **privacy manifest** (`PrivacyInfo.xcprivacy`) and fill in App Privacy in
  App Store Connect to match the Privacy Policy.
- Account deletion is in Account → Security → Delete Account, which App Review
  checks for.
- **Crash reporting:** add the Sentry Swift package to the app target (File → Add
  Package Dependencies → `https://github.com/getsentry/sentry-cocoa`, product
  `Sentry`) and set `SENTRY_DSN` in `Release.xcconfig` or `Local.xcconfig`, written
  as `https:/$()/key@o0.ingest.sentry.io/0` because xcconfig treats `//` as a
  comment. `iOSApp.swift` starts Sentry only when the package is linked and the DSN
  is set. Upload dSYMs (Sentry's Xcode build phase or `sentry-cli`) for readable
  stack traces.

## Hosting options

All of these can run the GHCR image as-is. Prices and plan limits change often —
check them before deciding. This is a comparison, not a recommendation.

| | Fly.io | Render | Railway | VPS (Hetzner, DigitalOcean, Lightsail…) + Docker |
|---|---|---|---|---|
| Runs the image | `fly deploy --image ghcr.io/…` | Web Service from an image URL | Service from an image | `docker compose pull && up -d` |
| Postgres | Fly Managed Postgres, or external | Render Postgres (PITR on paid plans), or external | Railway Postgres, or external | External managed DB recommended; self-hosting means owning backups (WAL-G/pgBackRest) |
| Migrations as a step | `release_command` in `fly.toml` | Pre-deploy command | Pre-deploy command | A `docker run … MigrateKt` line in your deploy script |
| Health checks | `[checks]` on `/ready` | Health check path `/ready` | Healthcheck path `/ready` | Docker `HEALTHCHECK` (built in) + your proxy |
| Zero-downtime deploys | Rolling by default | Yes | Yes | Only if you script it (two containers behind Caddy/Traefik) |
| TLS + two hostnames | `fly certs add` per host | Custom domains, automatic TLS | Custom domains, automatic TLS | Caddy obtains certificates automatically |
| Regions | Many, easy multi-region | A handful | A handful | Wherever the provider has data centres |
| Secrets | `fly secrets set` | Environment groups | Variables | `.env` file on the box (protect it) |
| Effort | Low–medium; CLI-driven | Low; mostly UI | Lowest; UI | Highest: OS updates, firewall, monitoring are yours |
| Cost shape | Pay per machine and volume | Fixed per instance tier | Usage-based | Cheapest per GB of RAM; your time is the cost |

Things to weigh: where your users are (latency to Lagos, London, etc.), whether
the database provider's PITR window meets your needs on the plan you can afford,
whether you want Git-push simplicity or full control, and who will be on call.

## What you need to provide

- [ ] Hosting platform and a managed PostgreSQL with PITR, for staging and production.
- [ ] A domain Splitfy owns (see [Domains and TLS](#domains-and-tls)); until then everything runs on the Render host.
- [ ] Production secrets: `JWT_SECRET`, `TOTP_ENCRYPTION_KEY`, database credentials, `RESEND_API_KEY` (and a verified sending domain).
- [ ] GHCR access for the host (make the package public, or a read-only token).
- [ ] Sentry organisation with three projects (API, Android, iOS) and their DSNs.
- [ ] Optional: `METRICS_TOKEN` and somewhere to scrape `/metrics`.
- [ ] Android upload keystore and the four signing secrets; Play Console app with Play App Signing; the app-signing SHA-256 for `ANDROID_CERT_SHA256`.
- [ ] Apple Developer team id, Sign in with Apple and Associated Domains capabilities, `APPLE_APP_IDS`, the Sentry Swift package added in Xcode.
- [ ] A monitored support inbox (the apps' Contact Support opens `SupportContact.EMAIL`).
- [ ] Legal review of the Terms of Service and Privacy Policy (`app/shared/.../screens/account/legal/LegalCopy.kt`), the bracketed items filled in, and `LEGAL_REVIEW_PENDING` set to `false`; publish the same text at a public URL for the store listings.
