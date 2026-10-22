# Architecture

## Why one repository

The app and the API describe the same domain. Kept apart, they drift: the client
rounds a three-way split one way, the server another, and the difference shows up as
a kobo that nobody owes. A single repository lets the two share one module — and the
sharing, not the co-location, is the point.

```
    ┌──────────────────┐     ┌──────────────────┐
    │ app:androidApp   │     │   app:iosApp     │
    └────────┬─────────┘     └────────┬─────────┘
             └──────────┬─────────────┘
                ┌───────▼──────────┐     ┌────────────────────┐
                │    app:shared    │────▶│ app:ui-components  │  theme · composables
                └───────┬──────────┘     └────────────────────┘
                        │                screens · ViewModels · repositories · HTTP
                ┌───────▼──────────┐
                │       core       │  DTOs · domain models · split maths · validation
                └───────▲──────────┘
                        │
                ┌───────┴──────────┐
                │     backend      │  routes · services · repositories · Postgres
                └──────────────────┘
```

## `:core`

Targets `android`, `iosArm64`, `iosSimulatorArm64` and `jvm`. Contains no framework
code — no Ktor, no Compose, no Exposed — so it stays cheap to compile and impossible
to couple to a transport.

**Money.** Amounts are `Long` minor units plus an ISO-4217 code. `Money.allocate`
divides an amount across weights using the largest-remainder method, which guarantees
the parts sum back to exactly the total. Floating point is never used: a `Double`
cent error compounds across a group's lifetime into a balance nobody can reconcile.

**Splits.** `SplitCalculator` turns a total plus a `SplitInput` (equal, exact,
percentage, shares) into per-participant amounts, returning a typed `SplitOutcome`
rather than throwing. The client uses it to preview a split offline; the server uses
it to compute what it stores. The client never sends amounts — only inputs — so a
tampered or out-of-date client cannot persist shares that do not add up.

**Balances.** `BalanceCalculator` folds the expense and settlement ledger into net
positions, then reduces them to a near-minimal set of transfers. Balances are never
stored, so a re-synced device always converges on the server's numbers.

**Contracts.** `ApiRoutes` holds every path; the server registers routes from these
constants and the client builds requests from them. `ApiErrorCode` and
`ApiErrorResponse` are the single error envelope. `Validators` are run on both sides —
on the client for inline field errors, on the server because client validation is a
UX affordance, not a security control.

## `:backend`

Layered, with dependencies pointing inward:

```
routes → services → repositories → Exposed/Postgres
```

- **Routes** parse, authorise via the service, and respond. No business rules.
- **Services** own the rules: membership checks, currency matching, split
  recomputation, settlement confirmation rights.
- **Repositories** own SQL and mapping. Nothing above them sees a JDBC type.

**Authentication.** Short-lived JWT access tokens (15 min) carry no server state, so
the hot path has no database round trip. Refresh tokens are opaque, stored as a
SHA-256 digest, and rotate on every use. Presenting a token that was already rotated
away is treated as theft: every session for that user is revoked. Passwords use
BCrypt with a configurable cost, and a login against an unknown address still runs a
hash so timing does not reveal whether an account exists.

**Authorization.** A caller who is not a member of a group gets `404`, not `403` —
confirming that a group id exists is itself a leak.

**Errors.** Every deliberate failure is an `AppException` with a status and an
`ApiErrorCode`; one `StatusPages` handler renders them. Unexpected exceptions are
logged with a stack trace and returned as a generic 500, because exception messages
routinely contain SQL and user data.

**Idempotency.** Expense and settlement creation accept a `clientRequestId`, unique
per group at the database level. A retry after a timeout returns the original row
instead of double-charging the group.

**Pagination.** Keyset (`created_at`, `id`) rather than offset, so an infinite scroll
stays stable while other members are adding expenses above the cursor.

**Migrations.** Flyway owns the schema; the Exposed table objects are the typed view
of it. Migrations run at boot, before the instance reports ready.

**Observability.** Every request carries an `X-Request-Id` (generated when absent),
put into the MDC, echoed on error responses as `traceId`, and printed on every log
line. `/health` is a pure liveness check that touches nothing; `/ready` verifies the
database, so a broken instance leaves the load balancer instead of being killed.

## `:backend` — money

The wallet is a **cached fold of an append-only ledger**. Every movement writes one
`ledger_entries` row carrying the balance as it stood afterwards, so a statement
printed today still reads the same next year, and a disputed balance is settled by
replaying the table.

Balance changes take a row lock (`SELECT … FOR UPDATE`) for the duration of the
transaction: two concurrent withdrawals cannot both read the same balance and each
conclude there is enough money. A database `CHECK (balance >= 0)` backs the service
rule, so no future code path can overdraw an account.

Peer transfers come in two shapes. A `PAYMENT` debits the sender and credits the
recipient in one call. A `REQUEST` creates only a pending record — money moves when
the person being asked approves it, so nobody can pull funds from someone else's
wallet. Every write takes an idempotency key.

**Card data never reaches the backend.** The device exchanges card details with the
payment provider for an opaque token and posts only that token plus the last four
digits; a request whose `providerToken` looks like a card number is rejected outright.
That keeps the API out of PCI scope. `DevCardTokenizer` stands in for the provider
SDK — it validates (Luhn, expiry, CVV) and issues a fake token, and cannot charge
anything.

## `:app:shared`

Packages are flat, following the KotlinConf app: a screen sits next to its view
model rather than in a folder of its own.

```
screens/        Screen composables + ViewModel + UiState (StateFlow)
repositories/   repository interfaces and their implementations
network/        Ktor client, API config, error mapping
storage/        session storage
navigation/     routes and the NavHost
di/             Koin modules
(root)          DataResult/DataError, SessionManager, CardTokenizer, shared value types
```

**Failures.** `ApiCaller` is the one place a transport exception becomes a
`DataError`. No `IOException` or HTTP status escapes the data layer, so a ViewModel
branches on `Offline`, `Validation`, `Unauthenticated` and friends instead of
inspecting Ktor types.

**Sessions.** Ktor's Bearer provider refreshes once on a 401 and replays the original
request, so screens never see a transient expiry. A genuinely dead session calls
`SessionManager.signOut()` exactly once, and the navigation graph unwinds the whole
back stack in response.

**Storage.** iOS keeps tokens in the Keychain. Android currently uses private
SharedPreferences — see the follow-ups below.

**DI.** Koin, started from each platform's entry point. Repositories are bound to
interfaces and ViewModels are `factory` bindings scoped to their navigation entry.

## Build

- One Gradle build, one version catalog, one wrapper.
- AGP 9 no longer supports `com.android.application` on a KMP module, so the Android
  app shell (`:app:androidApp`) is separate from the shared UI (`:app:shared`).
- Timestamps use `kotlin.time.Instant` from the Kotlin 2.3 standard library rather
  than `kotlinx-datetime`, which removed the type its 0.6 line exposed. The server
  converts to `java.time.Instant` at the repository boundary, where Exposed needs it.

## Screen wiring

Every screen reads from the API through a repository; no screen renders hardcoded
sample data. A few notes on the less obvious ones:

- **Split preview** — the add-expense screen runs `:core`'s `SplitCalculator`
  on-device, so the per-person amounts shown before saving are the amounts the server
  stores. The client sends split *inputs*, never computed shares.
- **Activity** — derived from the ledger on read using the same calculator the group
  screen uses, so it cannot disagree with a group's balances.
- **Contacts** — people the user already shares a group with. There is no separate
  address book, so the app never asks for phone-book access. The directory search is
  separate and only fires past the server's minimum query length, which is what stops
  the endpoint being walked to enumerate users.
- **Statement** — the wallet ledger itself, filtered server-side so paging stays
  correct rather than filtering only the page already in memory.
- **Pay / Request** — the counterparty list is contacts with a non-zero balance, and
  the proposed amount is exactly what is outstanding with that person.
- **Archive, not delete** — a group is archived rather than destroyed; balances are
  history, and deleting would rewrite statements members have already seen.

## Known follow-ups

These are deliberate omissions, not oversights:

1. **Offline-first sync.** The PRD calls for a local database and a sync engine. The
   repository interfaces are the seam for it: a SQLDelight local data source slots in
   behind them without touching a ViewModel. Today the client is online-only.
2. **Android token storage.** SharedPreferences is app-private but not encrypted at
   rest. Move to the Android Keystore. (iOS already uses the Keychain.)
3. **OTP delivery.** `LoggingOtpNotifier` prints reset codes to the log and refuses to
   run in production. A real email/SMS provider implements `OtpNotifier`.
4. **Payment provider.** `DevCardTokenizer` and the top-up/withdraw endpoints move
   money inside splitfy's own ledger only; no external rail is connected yet. Swap in
   the provider SDK on the client and settle the ledger against it on the server.
5. **QR decoding.** The scan flow captures a frame and confirms the recipient against
   real contacts before paying, because the project has no QR decoder dependency yet.
   Paying the wrong person is unrecoverable, so the confirmation stays either way.
6. **Multi-currency conversion.** Balances are computed per currency and never summed
   across them. Cross-currency groups need an FX rate source before that changes.
7. **Subscription billing.** The upgrade-plan screens are presentational; there is no
   entitlement model on the server.
8. **Invite install handoff.** Someone without the app installs it and then has to
   reopen the invite link; the invite isn't carried through the store. See
   [GROUP_INVITES.md](GROUP_INVITES.md).
