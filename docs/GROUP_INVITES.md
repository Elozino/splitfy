# Group invite links

Group detail → menu → **Invite by link** opens the native share sheet. All members
can share the current link. Owners/admins can reset it after confirmation; old links
then stop working. Links expire after 30 days. The current design has no usage cap.

Opening a link shows the group name, inviter and member count before joining.
Signed-out users continue through the existing authentication flow, then return to
the invitation. Pending tokens are stored until joining or choosing **Not now**.
Existing members can accept again without duplicate memberships or use counts.

## Deployment

The mobile apps currently claim `https://splitfy.onrender.com/i/*`, the Render host, until Splitfy
has its own domain. Set these backend values:

- `PUBLIC_BASE_URL=https://splitfy.onrender.com`
- `ANDROID_PACKAGE`: the installed application's package name
- `ANDROID_CERT_SHA256`: comma-separated SHA-256 signing-certificate fingerprints;
  for Play-distributed builds use the Play app-signing certificate
- `APPLE_APP_IDS`: comma-separated `TEAMID.bundle.identifier` values for signed apps

Route `/i/*`, `/.well-known/assetlinks.json`, and
`/.well-known/apple-app-site-association` on the invite host to the backend over
HTTPS. On `splitfy.onrender.com` the backend serves them directly. These public paths must not require sign-in. Changing the invite hostname
also requires changing the Android manifest, iOS associated-domain entitlement,
and the shared link parser.

Enable Associated Domains for the iOS signing profile. Both apps also register
`splitfy://invite/{token}` for the landing page's **Open in splitfy** button. A user
without the app must install it and reopen the invitation; deferred install links
are not implemented.

## Device verification before release

1. Share from a group on Android and iOS (including the iPad share popover).
2. Open an HTTPS invite with the app closed, then with it running.
3. While signed out, open a link, sign up and verify email, then join the same group.
4. Restart during authentication and confirm the pending invitation is retained.
5. Reset a link and confirm the original is rejected; also test an archived group.
6. Test the browser fallback without the app installed.

The automated suite covers the API, real PostgreSQL migration, simultaneous link
creation/joining, link validation and persistence. Signed-device checks still need
the production domain and signing configuration above.
