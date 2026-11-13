# Pairshot

Before & after job photos for service businesses — pressure washing, detailing, cleaning, landscaping.
Shoot the before, shoot the after lined up with a ghost of the before, send the client one clean link.

By Grease Pencil Studio. The full plan is in [PLAN.md](PLAN.md).

## Status

**Phase 1 (offline core) — in progress.** Works today, fully offline:
- Jobs (create, edit, delete, search)
- Before camera: just shoot; labels ("Driveway") are optional and can be added or changed later
- After camera: walks every before that still needs its after, in shooting order. The before shows
  as a red outline (default), a see-through photo, or nothing (Off); hold to peek; zoom restored
- Volume keys work as the shutter
- Pairs: slider compare, label, make cover, hide from client, swap, retake, delete
- Import from the gallery: befores in bulk, or a single after for a pair (system Photo Picker)
- Share one pair as a single side-by-side or stacked image
- Client page preview: the exact page the client will get, built on the phone

**Phase 2/3 (links and CRM): built, running on a local server for now.**
- Send link: photos are prepared on the phone (upright, resized, location and EXIF removed),
  only new ones are uploaded, and the share sheet opens with your message filled in. "Text" goes
  straight to the client's number.
- No signal? The link waits and goes by itself, with a notification if the app is closed.
- One job, one link. Updating keeps the same link and re-uploads nothing. Closing it shows the
  client a polite page and deletes the photos from the server. You can see when the client opens it.
- Free: 3 new links a month, 30 photos each, live 14 days, editable for 3 days. All enforced by the server.
- Pro (test switch for now): CRM jobs arrive on the phone, webhooks tell your CRM about sends and
  views, and API keys for Zapier and Make. See [docs/api.md](docs/api.md).
- How to try it on a phone: [docs/testing-cloud.md](docs/testing-cloud.md).

Live at https://pairshot.pairshot-server.workers.dev (test mode). Google sign-in is built and waits for a
Google Cloud client ID ([docs/google-sign-in.md](docs/google-sign-in.md)). Buying Pro (Google Play Billing, checked by the server) switches on with [docs/play-billing.md](docs/play-billing.md).

## Build and run

Open the `android/` folder in Android Studio and press Run, or from a terminal:

```
cd android
./gradlew :app:installDebug      # Windows: gradlew.bat :app:installDebug
```

Needs JDK 17+ and the Android SDK with platform 36. The debug build installs as
`com.greasepencil.pairshot.debug`, next to any release build.

## Tests

```
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug

cd shared/gallery
npm ci && npm test

cd server
npm ci && npm run typecheck && npm test
```

- JVM unit tests for the pure logic (after queue, label suggestions, home ordering, edge outline,
  collage layout, rotation, scheduling, client page data)
- Robolectric tests of the database, files and every schema migration
- The client page template run in a simulated browser (rendering, escaping, links, slider)
- Android lint (fails the build on errors)
- Server: 66 tests run inside the real Workers runtime (local D1 and R2): every limit, galleries,
  photo checks, CRM field reading, signed webhooks with retries, the API, cleanup
- App ↔ server: the send pipeline, CRM sync and HTTP client against a fake server, plus an
  end-to-end test against the real one (`PAIRSHOT_SERVER=…`, see docs/testing-cloud.md)
CI runs them and builds the APK on every push (`.github/workflows/android.yml`); the APK is
attached to each run as an artifact.

## Layout

```
android/              the app (Kotlin, Jetpack Compose, Room, CameraX, Hilt)
server/               the Cloudflare Worker: links, client pages, CRM, webhooks, API (TypeScript, Hono, D1, R2)
shared/gallery/       the client page template, used by the app preview and the server
docs/grease-pencil/   Grease Pencil Studio signature kit (rules, loop, fonts, logo)
PLAN.md               the product and build plan
```
