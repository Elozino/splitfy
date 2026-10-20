# Splitfy — Implementation Plan to Launch

Initial review: 2026-10-20, based on `main` (`a0ab83d`) against
`docs/split_bill_app_prd.md`. Phase 4 was updated after the invite implementation;
other phase checkboxes still reflect the original review.

**Database:** PostgreSQL is the system of record. Every schema change in this plan is a
new Flyway migration in `backend/src/main/resources/db/migration/` (next is `V6__…`, after the local invite migration).
The on-device cache in Phase 8 is SQLite (via SQLDelight); it is a cache of
Postgres data, not a second source of truth.

## Milestones

| Milestone | Phases | Outcome |
|---|---|---|
| **M1 — Closed beta** | 0, 1, 2, 3, 6A | Friends can sign up, form groups, split, and settle (recorded outside the app) |
| **M2 — Public v1** | 4, 5, 7, 10 | Invite links, receipts, push, store releases, production infra |
| **M3 — v1.x** | 8, 9, 6B | Offline mode, multi-currency, real money movement |

Sizes: **S** ≤ 1 day · **M** 2–4 days · **L** 1–2 weeks.

---

## Phase 0 — Repository hygiene (blocker) · S

The app's screens are not in git, so `main` does not compile for anyone else.

- [ ] Commit `app/shared/src/commonMain/kotlin/com/elozino/splitfy/screens/` and
      `app/ui-components/src/commonMain/composeResources/values/`.
- [ ] Add `.claude/worktrees/` to `.gitignore`; decide whether the `.run/` configs are shared (commit) or personal (ignore).
- [ ] Confirm every CI job (`jvm`, `android`, iOS if present) is green on a clean clone.

**Done when:** a fresh clone builds `:app:androidApp:assembleDebug` and the iOS framework, and CI passes.

## Phase 1 — PostgreSQL in tests and production config · M

Backend tests currently run on H2 with Flyway **disabled** (`backend/src/test/.../TestSupport.kt`),
so the real migrations are never exercised.

- [x] Boot tests against real PostgreSQL (`io.zonky.test:embedded-postgres`, no Docker) with Flyway **enabled**; one server per test run, one database per test class.
- [x] Remove the H2 dependency.
- [x] Schema test: every Exposed column exists in the migrated database with matching nullability.
- [x] Production guard in `AppConfig.kt`: refuse to start in `PRODUCTION` with the default `DATABASE_PASSWORD`.
- [x] Statement timeout (`DATABASE_STATEMENT_TIMEOUT_SECONDS`, default 30s).

**Done when:** `./gradlew :backend:test` runs on Postgres in CI and the production boot checks are covered by tests.

## Phase 2 — Finish the core loop in the app · L

The backend supports everything here already; this is app wiring.

**Settle Up** (`screens/groups/detail/main/GroupDetailScreen.kt:123`)
- [ ] New `screens/settlements/` screen: show `GET /groups/{id}/balances` suggested transfers.
- [ ] "Mark as paid" → `POST /groups/{id}/settlements` with a `clientRequestId`.
- [ ] Payee sees pending settlements and can confirm (`…/confirm`) or the payer can cancel (`DELETE`).
- [ ] Balances refresh after confirm; activity feed entry shows up.

**Navigation fixes**
- [ ] `NavSetup.kt:264`: group settings should open `GroupSettingsScreen`, not `SplitDetail`.
- [ ] Group settings: rename, remove member, archive — all endpoints exist.

**Dead buttons (about 20 handlers)**
- [ ] Search on Groups / Activity / Group detail: local filter over loaded data.
- [ ] `AddExpenseScreen`: remove participant, split-settings button.
- [ ] Contacts: Pay / Request → existing `screens/payments/pay|request` with the contact pre-filled.
- [ ] Social sign-in (`NavSetup.kt:157`): **hide the buttons** for M1 (Apple review rejects non-working buttons). Real Google/Apple sign-in is a later item.
- [ ] Hide screens for features not in M1 behind a `FeatureFlags` object in `:app:shared`
      (billing/upgrade plan, linked accounts, scan-to-pay, and the wallet; see 6A).

**Tests**
- [ ] ViewModel tests for AddExpense, GroupDetail and Settlement using the existing `MockEngine` pattern in `AuthRepositoryTest`.

**Done when:** a user can create a group, add expenses with each split method, settle, and see balances hit zero, with no inert controls left on screen.

## Phase 3 — Real OTP / email delivery · M

The production server refuses to boot because only `LoggingOtpNotifier` exists (`AuthService.kt:192`).

- [ ] Implement `EmailOtpNotifier` using a transactional email API (Resend, Postmark or SES) via the Ktor client.
- [ ] Config: `EMAIL_PROVIDER_API_KEY`, `EMAIL_FROM`; bind it in `ServerModule.kt` when not `LOCAL`.
- [ ] Templates for: sign-up verification, password reset.
- [ ] Rate limits already exist; add a per-email resend cooldown.
- [ ] Verify the full flow in the app: sign up → OTP → verified; forgot password → OTP → new password.

**Done when:** a staging deploy boots with the real notifier and both flows work end to end.

## Phase 4 — Group invites · M

Implemented locally on 2026-10-20, continuing the unfinished invite backend.

- [x] Migration `V5__group_invites.sql`, repository, service and shared API contracts.
      V3 and V4 are already used by email verification and social identities.
- [x] Member-only shared link creation/retrieval, owner/admin reset (revokes old links),
      public preview and authenticated, idempotent acceptance.
- [x] Android App Links and iOS Universal Links for `https://splitfy.app/i/{token}`,
      with `splitfy://invite/{token}` fallback and a browser landing page.
- [x] Share sheet from group detail; preview and join dialog; persist pending links
      through existing sign-in/sign-up flows and app restarts. Onboarding UI unchanged.
- [x] Tests for permissions, expiry, revoked/archived links, repeated/concurrent joins,
      link parsing and pending-link storage; include invite table in schema validation.
- [ ] Production domain verification: route `/i/*` and `/.well-known/*` to the backend,
      set signing-certificate fingerprints and Apple app identifiers, then verify links
      and native sharing on signed Android/iOS builds.
- [ ] Optional maximum-use limits: the current shared-link design has a 30-day expiry,
      reset/revocation and use counting, but no configurable `max_uses` cap.
- [ ] Phone-number invites (SMS) remain deferred until an SMS provider is chosen.

Deployment setup and device checks: [GROUP_INVITES.md](GROUP_INVITES.md).

## Phase 5 — Receipts and group images · M

- [x] Object storage (S3-compatible: AWS S3 or Cloudflare R2). Postgres stores only the URL and key.
- [x] Endpoint for a presigned upload URL; validate content type and size; the server writes `receipt_url` after upload.
- [x] KMP image picker (FileKit or Peekaboo) with client-side compression.
- [x] Wire `AddExpenseScreen` gallery button. Group covers are set from group detail once the group exists, not from `NewGroupScreen`.

## Phase 6 — Money movement (decision required)

`WalletService.topUp` credits balances without charging anything, and `withdraw` pays nothing out.
This cannot ship as is.

### 6A — Recommended for M1: launch without the wallet · S
- [ ] Hide Top Up, Withdraw, Payment Methods and wallet balance behind `FeatureFlags`.
- [ ] Disable the wallet endpoints in production (route-level flag) so they cannot be called directly.
- [ ] Settlements are "recorded" payments (cash, bank transfer outside the app). This is how Splitwise works.

### 6B — Real payments (M3) · L, plus compliance
Assuming Paystack (NGN default currency):
- [ ] Migration `V4__payment_intents.sql`: `payment_intents(id, user_id, kind, amount, currency, provider_reference UNIQUE, status, created_at, settled_at)`.
- [ ] Top up: initialize a Paystack transaction → the app opens checkout → **credit the ledger only from a verified webhook** (HMAC signature check, idempotent on the reference).
- [ ] Payment methods: store Paystack authorization codes from successful charges, never client-supplied tokens.
- [ ] Withdraw: Paystack Transfers API; ledger entry goes `PENDING → COMPLETED/REVERSED` from the transfer webhook.
- [ ] Nightly reconciliation job against the provider's records.
- [ ] **Legal:** holding user balances is a stored-value product with licensing and KYC implications in Nigeria. Get legal advice before building, or route settlements as direct payer-to-payee charges with no stored balance.

## Phase 7 — Push notifications · M

- [ ] Migration `V5__device_tokens.sql`: `device_tokens(user_id, platform, token UNIQUE, updated_at)`.
- [ ] Register and unregister endpoints; the app registers on login and unregisters on logout.
- [ ] FCM for Android, and for iOS through APNs (upload the APNs key to Firebase).
- [ ] `NotificationService.notify` also sends a push, respecting the user's preferences from `notification_settings`.
- [ ] Scheduled payment reminders for open balances older than N days (once a day, opt-out).

## Phase 8 — Offline-first · L

- [ ] SQLDelight (SQLite) in `:app:shared` for groups, members, expenses, balances, activity.
- [ ] Repositories read from the local DB as the single source for the UI and refresh from the network in the background.
- [ ] Outbox table for writes (expenses, settlements), replayed in order using the existing `clientRequestId` idempotency. The server (Postgres) wins on conflicts.
- [ ] UI sync-state indicators (pending / synced / failed), per PRD §16.0.

## Phase 9 — Multi-currency · M

- [ ] Migration: expenses gain `original_amount`, `original_currency`, `fx_rate`; balances stay in the group currency.
- [ ] `exchange_rates` table populated daily from a rates API. Snapshot the rate when an expense is created so history never changes.
- [ ] Currency picker on Add Expense; show the converted amount live, computed by the same `:core` split maths.

## Phase 10 — Release and operations · M (in parallel with Phases 4–7)

**Backend**
- [ ] Managed PostgreSQL (e.g. Neon, RDS or Cloud SQL) with automated backups and point-in-time recovery; separate staging and production databases.
- [ ] Deploy pipeline: build the fat JAR/image → run Flyway migrations → roll out; health checks on `/ready`.
- [ ] Secrets in the platform's secret store; never defaults.
- [ ] Monitoring and alerting on `/metrics`, error logs and slow queries.

**Apps**
- [ ] Android: release signing config from CI secrets, Play Console internal track.
- [ ] iOS: signing, TestFlight, privacy manifest.
- [ ] Crash reporting (Sentry or Crashlytics) in both apps and the backend.
- [ ] Real content for Terms, Privacy and FAQ screens; account deletion (required by both stores).

---

## Decisions needed

1. **Wallet for v1:** 6A (record-only settlements, recommended) or 6B (real payments, needs legal review).
2. **Email provider** for OTP (Resend, Postmark or SES).
3. **Social sign-in:** hide for v1 (recommended), or build Google and Apple sign-in now.
4. **Hosting** for the backend and managed Postgres.
