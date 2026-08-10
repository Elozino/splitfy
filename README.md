# Splitfy

Bill splitting for groups — one Kotlin Multiplatform repository containing the
Android app, the iOS app, and the backend that serves them.

## Layout

```
splitfy/
├── shared/        KMP · contracts + domain logic used by the apps AND the server
├── composeApp/    KMP · Compose Multiplatform UI + client data layer (Android, iOS)
├── androidApp/    Android application host (manifest, icons, one Activity)
├── iosApp/        Xcode project hosting the shared Compose UI
├── server/        JVM · Ktor API, Postgres, Flyway migrations
└── docs/          Architecture notes and the product brief
```

`:shared` is the reason the merge is worth doing. It holds the API contract (DTOs,
route constants, error codes), the domain models, the **split and settlement maths**,
and the request validators. The client and the server compile the same code, so a
split previewed on a phone is byte-identical to the split the server persists, and a
renamed endpoint is a compile error rather than a 404 in production.

## What the app does

Groups and expenses with four split methods, balances reduced to a minimal set of
settling transfers, a wallet with an append-only ledger, peer payments and requests,
an activity feed, notifications, and contacts derived from shared groups. Every
screen reads from the API — there is no sample data left in the client.

## Running it

### Backend

```bash
docker compose up --build
```

Postgres and the API come up together; migrations run on start-up. The API is on
`http://localhost:8080`, with interactive docs at `http://localhost:8080/openapi`,
liveness at `/health` and readiness at `/ready`.

To run the server directly against a local Postgres instead:

```bash
./gradlew :server:run
```

### Android

```bash
./gradlew :androidApp:installDebug
```

Debug builds point at `http://10.0.2.2:8080` — the host machine as seen from the
emulator. Release builds point at the production host.

### iOS

Open `iosApp/iosApp.xcodeproj` in Xcode and run. The build phase invokes Gradle to
produce the `ComposeApp` framework, so no separate step is needed.

## Tests

```bash
./gradlew :shared:jvmTest :server:test :composeApp:iosSimulatorArm64Test
```

- `:shared` — split maths, rounding, balance/settlement algebra
- `:server` — full HTTP integration tests against an in-memory database
- `:composeApp` — data-layer mapping and session handling over a mocked transport

## Configuration

The server reads everything from the environment (see
`server/src/main/resources/application.yaml`). Nothing sensitive is committed, and
the app refuses to boot in `PRODUCTION` with the development JWT secret still set.

| Variable | Purpose | Default |
| --- | --- | --- |
| `APP_ENV` | `LOCAL` / `STAGING` / `PRODUCTION` | `LOCAL` |
| `PORT` | HTTP port | `8080` |
| `DATABASE_URL` | JDBC URL | local Postgres |
| `DATABASE_USER` / `DATABASE_PASSWORD` | Credentials | `splitfy` |
| `JWT_SECRET` | Access-token signing key (32+ chars) | dev-only value |
| `JWT_ISSUER` / `JWT_AUDIENCE` | Token claims | `api.splitfy.app` / `splitfy-mobile` |
| `BCRYPT_COST` | Password hashing work factor | `12` |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the layers fit together and
what is deliberately left for later.
