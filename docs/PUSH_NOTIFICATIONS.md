# Push notifications

The server sends every push through **Firebase Cloud Messaging (HTTP v1)**. Android
receives them directly; iOS receives them through APNs, which Firebase relays to with
an APNs key you upload. Push is optional at every layer: with nothing configured the
server logs what it would send, Android builds without `google-services.json`, and
iOS builds without the Firebase SDK.

## What gets pushed

| Event | Who | Preference switch |
|---|---|---|
| Expense added | Every other group member (with their share) | `newExpenses` |
| Payment recorded | The other party (payee is asked to confirm) | `settlements` |
| Payment confirmed / cancelled | The other party | `settlements` |
| Someone joins with your invite link | Whoever made the link | `groupActivity` |
| Wallet top-up, withdrawal, transfers | As today | `settlements` |
| Daily payment reminder | Members who have owed money for `PAYMENT_REMINDER_AFTER_DAYS` | `paymentReminders` |
| Security / account | The user | always on |

Preferences only gate the push; the in-app notification list keeps everything. A
reminder that is switched off is skipped entirely. Each push carries
`data.groupId`, and tapping it opens that group in the app.

Reminders run in every API instance at `PAYMENT_REMINDER_HOUR_UTC`; the first to
insert the day into `job_runs` does the work, and `payment_reminders` limits each
debtor to one reminder per group every `PAYMENT_REMINDER_EVERY_DAYS`. An instance
that starts after the hour runs that day's reminders immediately (if nobody has).

## One-time setup

### 1. Firebase project

1. In the [Firebase console](https://console.firebase.google.com/) create a project
   (or reuse the one used for Google sign-in).
2. **Add an Android app** with package `com.elozino.splitfy`, and a second one with
   `com.elozino.splitfy.debug` for debug builds. Download `google-services.json`
   (it lists both) and save it as `app/androidApp/google-services.json`. It is
   gitignored; CI that should ship push needs it written from a secret.
3. **Add an iOS app** with the bundle id from `app/iosApp/Configuration/Config.xcconfig`.
   Download `GoogleService-Info.plist`.

### 2. Server credentials

1. Firebase console → Project settings → **Service accounts** → *Generate new private key*.
2. Give the server that JSON, either inline or as a file:
   - `FCM_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}'`, or
   - `FCM_SERVICE_ACCOUNT_PATH=/run/secrets/fcm.json`
3. Boot logs `Sending push notifications through FCM`. A missing or unreadable key
   logs a warning and falls back to logging; it never stops the server.

The key needs only the *Firebase Cloud Messaging API (V1)*, which is enabled by
default on new projects (Project settings → Cloud Messaging).

### 3. APNs key (iOS)

1. [Apple Developer](https://developer.apple.com/account/resources/authkeys/list) →
   Keys → **+** → enable *Apple Push Notifications service (APNs)* → download the
   `.p8` and note the Key ID and your Team ID.
2. Firebase console → Project settings → Cloud Messaging → *Apple app configuration*
   → upload the `.p8` with the Key ID and Team ID. One key covers development and
   production.

### 4. iOS app

The Swift code (`app/iosApp/iosApp/AppDelegate.swift`) compiles with or without
Firebase thanks to `#if canImport(FirebaseMessaging)`. To turn push on:

1. Xcode → File → *Add Package Dependencies…* →
   `https://github.com/firebase/firebase-ios-sdk` → add **FirebaseCore** and
   **FirebaseMessaging** to the `iosApp` target.
2. Drag `GoogleService-Info.plist` into the `iosApp` folder (target membership:
   iosApp). It is gitignored.
3. Signing & Capabilities: check that **Push Notifications** is listed (the
   entitlement `aps-environment` is committed) and that *Background Modes → Remote
   notifications* is on (committed in `Info.plist`). With automatic signing Xcode adds
   the capability to the App ID; push needs a paid developer team.
4. Run on a **real device** (the simulator can't get an APNs token from a real
   server push). Sign in; accept the prompt; the FCM token is registered.

### 5. Android app

Nothing beyond `google-services.json`. On Android 13+ the app asks for the
notification permission after sign-in. Notifications use the `splitfy_default`
channel ("Activity").

## Checking it end to end

1. Run the server with the service account set.
2. Sign in on a device as user A and accept notifications. `device_tokens` gets a row.
3. As user B in a shared group, add an expense. A's phone shows "New expense in …";
   tapping it opens the group.
4. Turn off *New expenses* in Account → Notification and repeat: no push, but the
   notification still appears in the app's list.
5. Sign out on A's phone: the `device_tokens` row disappears.

## Known limits

- A session that ends because the refresh token expired (not an explicit sign-out)
  can't unregister, since the request would be unauthenticated. The token is moved
  to whoever signs in next on that phone, or dropped when FCM reports it dead.
- The reminder job walks every non-archived group once a day, which is fine for
  thousands of groups; past that it should page or move to a worker.
