# splitfy

Bill splitting for groups — one Kotlin Multiplatform repository containing the
Android app, the iOS app, and the backend that serves them.

## Layout

The module layout follows the one JetBrains use for the
[KotlinConf app](https://github.com/JetBrains/kotlinconf-app): a `core/` contract
module, a `backend/`, and everything client-side grouped under `app/`.

```
splitfy/
├── core/                  KMP · contracts + domain logic used by the apps AND the backend
├── backend/               JVM · Ktor API, Postgres, Flyway migrations
├── app/
│   ├── shared/            KMP · Compose Multiplatform screens + client data layer
│   ├── ui-components/     KMP · design system — theme, composables, icons, fonts
│   ├── androidApp/        Android application host (manifest, icons, one Activity)
│   └── iosApp/            Xcode project hosting the shared Compose UI
└── docs/                  Architecture notes and the product brief
```

Inside `app/shared` the packages are flat, as they are upstream: `screens/` holds
every screen next to its view model, with `network/`, `repositories/`, `storage/`,
`navigation/` and `di/` alongside it.

`:core` is the reason the merge is worth doing. It holds the API contract (DTOs,
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
./gradlew :backend:run
```

### Android

```bash
./gradlew :app:androidApp:installDebug
```

Debug builds point at `http://10.0.2.2:8080` — the host machine as seen from the
emulator. Release builds point at the production host.

### iOS

Open `app/iosApp/iosApp.xcodeproj` in Xcode and run. The build phase invokes Gradle
to produce the `shared` framework, so no separate step is needed.

## Tests

```bash
./gradlew :core:jvmTest :backend:test :app:shared:iosSimulatorArm64Test
```

- `:core` — split maths, rounding, balance/settlement algebra
- `:backend` — full HTTP integration tests against an embedded PostgreSQL server, with the Flyway migrations applied (no Docker needed)
- `:app:shared` — data-layer mapping and session handling over a mocked transport

## Configuration

### Apps

App identity and the API host come from one committed file per platform, so neither
is hardcoded in Kotlin:

| Platform | File | Reaches the app as |
| --- | --- | --- |
| Android | `app/androidApp/config.properties` | `BuildConfig.API_BASE_URL`, `applicationId`, version, `@string/app_name` |
| iOS | `app/iosApp/Configuration/{Debug,Release}.xcconfig` | build settings, plus an `ApiBaseUrl` Info.plist entry |

Both have a gitignored sibling for personal values — `config.local.properties` and
`Configuration/Local.xcconfig`. Each is read last and wins, which is where a signing
`TEAM_ID` goes, or the LAN address of your machine when running on a real device
rather than an emulator:

```properties
# app/androidApp/config.local.properties
api.baseUrl.debug=http://192.168.1.42:8080
```

### Backend

The backend reads everything from the environment (see
`backend/src/main/resources/application.yaml`). Nothing sensitive is committed, and
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
