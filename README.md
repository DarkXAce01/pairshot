# Pairshot

Before & after job photos for service businesses — pressure washing, detailing, cleaning, landscaping.
Shoot the before, shoot the after lined up with a ghost of the before, send the client one clean link.

By Grease Pencil Studio. The full plan is in [PLAN.md](PLAN.md).

## Status

**Phase 1 (offline core) — in progress.** Works today, fully offline:
- Jobs (create, edit, delete, search), areas per job with suggested names per trade
- Before camera: pick an area, shoot; volume keys work as the shutter
- After camera: walks every before that still needs its after, with the before shown as a
  see-through ghost or a red outline, hold-to-peek, and the before's zoom restored
- Pairs: slider compare, make cover, hide from client, swap, retake, delete
- Share one pair as a single side-by-side or stacked image

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
./gradlew :app:testDebugUnitTest
```

JVM unit tests for the pure logic (after queue, area suggestions, home ordering, edge outline,
collage layout, rotation, scheduling) plus Robolectric tests of the database and file handling.
CI runs them and builds the APK on every push (`.github/workflows/android.yml`); the APK is
attached to each run as an artifact.

## Layout

```
android/              the app (Kotlin, Jetpack Compose, Room, CameraX, Hilt)
docs/grease-pencil/   Grease Pencil Studio signature kit (rules, loop, fonts, logo)
PLAN.md               the product and build plan
```
