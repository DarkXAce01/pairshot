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

Not built yet: client gallery links (Phase 2, Cloudflare), CRM (Phase 3), Pro (Phase 3).

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
```

- JVM unit tests for the pure logic (after queue, label suggestions, home ordering, edge outline,
  collage layout, rotation, scheduling, client page data)
- Robolectric tests of the database, files and every schema migration
- The client page template run in a simulated browser (rendering, escaping, links, slider)
- Android lint (fails the build on errors)
CI runs them and builds the APK on every push (`.github/workflows/android.yml`); the APK is
attached to each run as an artifact.

## Layout

```
android/              the app (Kotlin, Jetpack Compose, Room, CameraX, Hilt)
shared/gallery/       the client page template, used by the app preview and (soon) the server
docs/grease-pencil/   Grease Pencil Studio signature kit (rules, loop, fonts, logo)
PLAN.md               the product and build plan
```
