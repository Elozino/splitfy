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
| `FCM_SERVICE_ACCOUNT_JSON` | empty | Firebase service-account key (the JSON itself). Optional: without it pushes are only logged. See `docs/PUSH_NOTIFICATIONS.md`. |
| `FCM_SERVICE_ACCOUNT_PATH` | empty | Or a path to that key file. |
| `PAYMENT_REMINDERS_ENABLED` | `true` | Daily reminder for debts older than `PAYMENT_REMINDER_AFTER_DAYS` (`3`), at most every `PAYMENT_REMINDER_EVERY_DAYS` (`7`) per group, run at `PAYMENT_REMINDER_HOUR_UTC` (`9`). |
| `FEATURE_WALLET` | `false` | Wallet, top-up, withdrawal, payment methods and transfers. Leave off until a payment provider is integrated. |

## Email (Resend)

1. Create an account at resend.com and add your sending domain; publish the DNS
   records it gives you (SPF, DKIM) and wait for it to verify.
2. Create an API key with "sending access" for that domain.
3. Set `RESEND_API_KEY` and `EMAIL_FROM` (an address on the verified domain).

Without a key, `LoggingOtpNotifier` writes codes to the log at INFO, which is how
you read them during local development.

## Tests

```bash
./gradlew :backend:test
```

Tests run against real PostgreSQL binaries started inside the JVM (no Docker), with
every migration applied.
