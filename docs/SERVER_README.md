# splitfy backend

Ktor API backed by PostgreSQL. Flyway applies `backend/src/main/resources/db/migration`
on start-up. See the root `README.md` for how to run it.

## Configuration

Every setting comes from an environment variable read by `application.yaml`. The
defaults suit local development; the server **refuses to start in `PRODUCTION`**
while an insecure default is still in place (noted below).

| Variable | Default | Notes |
|---|---|---|
| `APP_ENV` | `LOCAL` | `LOCAL`, `STAGING` or `PRODUCTION`. Outside `LOCAL`, `X-Forwarded-For` is trusted, so run behind a proxy. |
| `PORT` | `8080` | |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/splitfy` | |
| `DATABASE_USER` | `splitfy` | |
| `DATABASE_PASSWORD` | `splitfy` | **Required in production.** |
| `DATABASE_POOL_SIZE` | `10` | |
| `DATABASE_RUN_MIGRATIONS` | `true` | |
| `DATABASE_STATEMENT_TIMEOUT_SECONDS` | `30` | Upper bound for any single statement. |
| `JWT_SECRET` | dev value | **Required in production**, at least 32 characters. |
| `JWT_ISSUER` / `JWT_AUDIENCE` / `JWT_REALM` | | |
| `JWT_ACCESS_MINUTES` / `JWT_REFRESH_DAYS` | `15` / `30` | |
| `BCRYPT_COST` | `12` | |
| `OTP_MINUTES` | `10` | Lifetime of a password-reset code. |
| `RESEND_API_KEY` | empty | **Required in production.** Without it, reset codes are only logged. |
| `EMAIL_FROM` | `splitfy <no-reply@splitfy.app>` | Must be on a domain verified in Resend. |
| `FEATURE_WALLET` | `false` | Wallet, top-up, withdrawal, payment methods and transfers. Leave off until a payment provider is integrated. |
| `STORAGE_ENDPOINT` | empty | S3 API endpoint, e.g. `https://<account>.r2.cloudflarestorage.com` or `https://s3.eu-west-1.amazonaws.com`. Uploads are off unless endpoint, bucket and both keys are set. |
| `STORAGE_BUCKET` | empty | |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | empty | Credentials allowed to read and write objects in the bucket. |
| `STORAGE_REGION` | `auto` | `auto` for R2; the bucket's region for AWS. |
| `STORAGE_PUBLIC_BASE_URL` | empty | Optional public host for **cover photos only**. Receipts always use signed links. |

## Email (Resend)

1. Create an account at resend.com and add your sending domain; publish the DNS
   records it gives you (SPF, DKIM) and wait for it to verify.
2. Create an API key with "sending access" for that domain.
3. Set `RESEND_API_KEY` and `EMAIL_FROM` (an address on the verified domain).

Without a key, `LoggingOtpNotifier` writes codes to the log at INFO, which is how
you read them during local development.

## File uploads (S3 or R2)

Receipts and group cover photos go straight from the app to object storage; the
server only signs URLs (SigV4, no SDK) and records which keys it issued.

1. Create a **private** bucket. On R2: create the bucket, then an R2 API token with
   "Object Read & Write" scoped to it. On AWS: a bucket with Block Public Access on,
   and an IAM user limited to `s3:PutObject` and `s3:GetObject` on `arn:aws:s3:::<bucket>/*`.
2. Set `STORAGE_ENDPOINT`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`
   (and `STORAGE_REGION` on AWS).
3. CORS is not needed for the mobile apps. A browser client would need a CORS rule
   allowing `PUT` (with the `Content-Type` header) and `GET` from its origin.
4. Optionally serve covers from a public host (`STORAGE_PUBLIC_BASE_URL`). Only do this
   if that host exposes `groups/*/covers/*` alone — an R2 public bucket or custom
   domain exposes every object, receipts included, protected only by their random
   names. Without it, covers are signed links that are stable for an hour.

Objects live under `groups/<groupId>/receipts/` and `groups/<groupId>/covers/`. A
lifecycle rule that deletes objects never attached to anything is a sensible later
addition; unattached uploads are harmless but take space.

## Tests

```bash
./gradlew :backend:test
```

Tests run against real PostgreSQL binaries started inside the JVM (no Docker), with
every migration applied.
