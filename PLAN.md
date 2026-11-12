# Pairshot — build plan

App name: **Pairshot** (repo DarkXAce01/pairshot). Published by Grease Pencil Studio.
Platform: Android first (Play Store). iOS later. **No ads, ever.**

## 1. What problem we solve

Service businesses (pressure washing, detailing, cleaning, landscaping, gutters, carpets) take 20–40
photos per job. Today they end up with a camera roll mess, manual sorting, and a photo dump sent to the
client.

We build the narrow tool: **shoot before → shoot after (perfectly aligned) → send one beautiful link.**
Quick, dead simple, no bloat, and it talks to the CRM so nobody re-types a job.

### The four things that must be excellent
1. **Capture speed.** Open app → in camera for the right job in ≤ 2 taps. The shutter never waits.
2. **Alignment.** The "ghost" before-photo overlay so every after matches the before's angle.
3. **The client page.** A branded, fast, mobile-first before/after slider page — not a photo grid.
4. **Never blocked by signal.** Everything works offline; sending finishes itself later.

### Out of scope (the "no bloat" list)
Timesheets, tasks/checklists, chat, project management, invoicing, estimates, scheduling, drawings,
PDF report builders. The CRM has those; we connect to it.

## 2. Competitor: CompanyCam

| | CompanyCam | Pairshot |
|---|---|---|
| Price | ~$79/month (Core, 1 user) up to $249/month; extra users ~$29–34/month each | Free tier; Pro is a **one-time** purchase |
| Before/after | Not on the cheapest plan; makes a combined image | Core of the app, on every plan |
| Built for | Construction teams, big projects | Small service crews, one-day jobs |
| Aligning the after shot | No ghost overlay found | Ghost overlay + edge outline, restores zoom |
| What the client gets | Gallery / timeline link (photo grid) | Branded slider page with review/book/call buttons |
| Account to start | Sign-up + trial | None until the first link is sent |
| Where they are stronger | Teams, iOS, many native integrations (Jobber etc.), reports, maturity | — we answer with Zapier/Make webhooks first, native Jobber later |

We win on price, focus, alignment and the client page. We don't try to match their team features.

## 3. How the app works

**First open (no account, ~30 s):** business name, trade (sets area chips, e.g. Driveway · Patio ·
Siding · Roof), optional logo → Home.

1. **Job arrives** from the CRM, or **+** with client, address, service.
2. **On site → Before.** Tap the job → camera. Pick an area chip, shoot. Each shot is a before waiting
   for its after. Volume key works as shutter.
3. **After.** Walks the "needs after" queue; the old photo is shown faintly over the live view (or as a
   red outline). Shoot → next. Zoom of the before is restored. Works with no signal.
4. **Review.** Pairs are already matched. Hide, re-pair, crop, pick the cover, reorder.
5. **Preview.** The exact client page, built on the phone from the same template — uploads nothing.
6. **Send.** First time only: Google one-tap sign-in. Only the gallery's photos upload; the share sheet
   opens with the message ready. The red loop draws itself in: **Sent.** Offline → "Will send when
   you're back online", then a notification "Dana's gallery is ready — tap to send."
7. **Client opens the link** → slider page with Leave a review / Book again / Call.
8. App shows **Seen**; Pro sends the link to the CRM as a note.
9. **Changes?** Edit → **Update gallery**: same link, only new photos upload.

Also, fully offline and free: **collage export** (side-by-side / top-bottom image with logo) for social
posts and Google Business.

**One job = one gallery = one link.**

## 4. Feature list

### v1
- Jobs (manual or from CRM), search, today's jobs first. Status: Upcoming / In progress / Done / Sent / Seen.
- Areas with preset chips learned from use; pairs (before + after) plus optional extras.
- CameraX camera: before mode, after mode with ghost (opacity slider, edges-only, peek), zoom restore,
  volume-key shutter, undo, thumbnails strip.
- Photo Picker import (no broad storage permission).
- Review: hide, swap, re-pair, crop (stored as numbers), cover, reorder.
- On-phone gallery preview; collage export.
- Send / update gallery; share sheet with message template; "Seen".
- Client page: branded, slider + side-by-side, fullscreen swipe, review/book/call, OG preview image,
  EXIF/GPS stripped, noindex, revocable.
- Pro: CRM inbound webhook, outbound webhooks (signed), API key + small REST API, delivery log.
- Settings: business & branding, area presets, ghost defaults, Wi-Fi only, local photo cleanup,
  integrations, delete cloud data, studio lockup + promise line.

### v1.1
Teams (invite code), FCM push for CRM jobs, client "download all", official Zapier app.

### Later
Native Jobber integration, post-capture auto-align (OpenCV), before→after reveal video, iOS.

## 5. Free and Pro

The only thing that costs money is **stored photos** in R2 (~0.4 MB each, $0.015/GB-month).
Requests, CRM calls, API calls, page views cost $0 (they use the free daily request allowance;
limits on them exist to stop abuse, not to save money).

| | Free | Pro — one-time **$9.99** (Play regional pricing) |
|---|---|---|
| Jobs, camera, ghost overlay, review, preview | Unlimited | Unlimited |
| Collage export | Unlimited, small "Made with Pairshot" mark | Unlimited, no mark |
| **New galleries** | **3 per calendar month** (deleting one does not give it back) | Unlimited (fair use, 5 GB stored ≈ 12,000 photos) |
| Photos per gallery | 30 | 100 |
| Gallery lifetime | 14 days | Never expires |
| Edit window after sending | 3 days | Any time |
| Gallery footer | "Made with Pairshot" | None — fully their brand |
| "Seen" status | ✓ | ✓ |
| CRM webhooks + API | — | ✓ |

Cost check: a maxed free user stores ≤ 3 × 30 × 0.4 MB = 36 MB → $0.0005/month.
A maxed Pro user (5 GB) costs ~$0.90/year; one sale ($8.49 after Google's 15 %) covers ~9 years of
the worst case. Teams (v1.1) may be a small subscription because they share storage across phones.

## 6. Gallery updates

**What:** editing a gallery after it's sent — fix a crop, hide a bad pair, add an after shot taken later.
Same link, so the client's text still works.

**Cost:** reorder / hide / crop / captions / cover / layout = **$0** (only the few-KB manifest changes).
New photos cost the same as any photo. Removed photos are deleted from R2 after 24 h.

**Anti-reuse rules** (so nobody turns one gallery into many jobs):
- The gallery is locked to its job: client name, address and service can't change after first send.
- Free: editing allowed for 3 days after sending. Pro: any time.
- Total different photos ever uploaded to one gallery ≤ 1.5× its photo limit (Free 45, Pro 150).
- Max 20 updates per gallery per day (both plans).
- Expiry counts from first send, never resets on update.

## 7. Limits (all enforced on the server)

| Limit | Free | Pro | Why |
|---|---|---|---|
| Accounts | 1 per Google ID, 1 free per phone | 1 per Google ID | Stops extra-account dodging |
| New galleries | 3/month | 50/day | Cost / abuse |
| Photos per gallery | 30 | 100 | Cost |
| Max file after resize | 1 MB | 1 MB | Cost |
| Account storage | 50 MB | 5 GB | Cost (the real cap) |
| Updates | 20/gallery/day, 3-day window | 20/gallery/day | Abuse |
| CRM inbound jobs | — | 200/day | Abuse (free otherwise) |
| API calls | — | 1,000/day | Abuse (free otherwise) |
| Requests per minute per account | 60 | 120 | Protects the shared daily allowance |
| Page views | Unlimited, cached | Unlimited | $0 |
| **Global brake** | Free uploads pause when total R2 storage passes the owner's set limit (start 10 GB) | Never paused | Hard cap on the bill |

## 8. Tech stack

### Android — Kotlin + Jetpack Compose
| Concern | Choice |
|---|---|
| UI | Compose, Material 3 themed to our look, single Activity, Navigation Compose |
| Architecture | MVVM / UDF, `StateFlow` per screen, repositories |
| DI | Hilt |
| Local DB | Room — the source of truth |
| Settings | DataStore |
| Camera | CameraX; Compose overlay for the ghost |
| Images | Coil 3 for display; own resize/EXIF pipeline |
| Background | WorkManager (unique work per gallery, network constraint, backoff) |
| Network | OkHttp + Retrofit + kotlinx.serialization |
| Auth | Google one-tap (Credential Manager), at first Send only |
| Abuse | Play Integrity (+ device recall for one free account per phone) |
| Billing | Play Billing, one-time in-app product "pro" |
| SDK | min 26, target/compile 36 |

### Backend — Cloudflare
| Piece | Use |
|---|---|
| Worker (TypeScript, Hono) | API, CRM webhooks, purchase verification, server-rendered gallery pages |
| D1 | accounts, galleries (manifests), photo refs, usage counters, view counts |
| R2 (card on file, $0 under 10 GB) | gallery photos: `display` 1600 px WebP, `thumb` 400 px, `og` 1200×630 |
| Queues | outbound webhooks with retries |
| Domain | short domain for links (~$10/yr), `workers.dev` until then |

Photos are resized/converted on the phone. Photos in R2 are keyed by content hash, so the server only
asks for files it doesn't already have.

### Monorepo
```
pairshot/
  android/            Android app
  server/             Cloudflare Worker
  shared/gallery/     the page template, used by the Worker AND bundled into the app for preview
  docs/               grease-pencil brand kit, api.md, zapier-recipes.md
```

## 9. How the hard parts work

### 9.1 Data model
```
Job      id, external_id?, source(manual|crm), client_name, client_phone, client_email, address,
         service, scheduled_at, status, notes, created_at, updated_at
Area     id, job_id, name, position
Photo    id, job_id, area_id, kind(before|after|extra), pair_id?, file, width, height, zoom_ratio,
         lens_facing, taken_at, hidden, crop, content_hash?
Pair     id, area_id, before_photo_id, after_photo_id?, is_cover, position, hidden
Gallery  id, job_id, slug, state(draft|queued|uploading|live|expired), sent_at, expires_at,
         last_manifest_hash, seen_at, view_count
```
IDs are UUIDs made on the phone. Server mirrors only Gallery + photo refs + account.

### 9.2 Send / update pipeline
1. App builds the manifest from Room (branding, sections, pairs, crops, captions).
2. App makes the WebP variants for photos not yet prepared, hashes them.
3. `POST /galleries/{id}/prepare` with manifest + hashes → server checks limits, replies with presigned
   R2 URLs for the hashes it doesn't have.
4. WorkManager uploads each file (resumable per file, idempotent), then `POST /galleries/{id}/publish`.
5. Server stores the manifest; the link is live. Old unreferenced files are deleted after 24 h.

### 9.3 Client page
Worker reads the manifest from D1 → fills `shared/gallery` template → HTML (~20 KB, vanilla JS slider).
Images come from R2 via the custom domain with long cache headers. Improving the template improves
every gallery ever sent. View counter: no cookies, no tracking scripts.

### 9.4 Ghost overlay
`PreviewView` with `FILL_CENTER`; the before is drawn on top with the same scale/crop so pixels line up.
Edges mode: Sobel filter on a small copy of the before → red outline bitmap. The before's zoom ratio is
re-applied when the after camera opens.

### 9.5 CRM (Pro)
- Inbound: `POST /hooks/in/{token}` with `external_id, client_name, client_phone, client_email,
  address, service, scheduled_at`; upsert by `external_id`; lenient field aliases. Phone pulls on open
  (push in v1.1).
- Outbound: `gallery.published`, `gallery.viewed`, `job.completed`, HMAC-SHA256 signed, retried 24 h.
- API key: list jobs, get a job's gallery link.

### 9.6 Accounts
Google one-tap → ID token → Worker verifies with Google's keys → our session token (encrypted on the
phone). Registration requires a Play Integrity verdict; device recall marks "free account used" on the
phone. Pro: Play purchase token sent to the Worker, verified with the Play Developer API.

## 10. Play Store checklist
- Closed test with **≥ 12 testers for 14 continuous days** before production (new personal account).
- Target SDK at Play's current requirement.
- CAMERA permission + Photo Picker only; no `READ_MEDIA_IMAGES`.
- Data safety form; privacy policy hosted on the Worker.
- Cloud-data deletion in the app **and** on a web page.
- Foreground service type `dataSync` for long uploads.

## 11. Brand
- Accent pencil red `#D4432A` / `#E5553A`; also the ghost outline colour.
- **The loop moment:** "Sent." — the loop draws round the cover photo.
- Icon: the loop around a split before/after square.
- Settings ends with `StudioLockup` + promise line, e.g. `PAIRSHOT 1.0.0 · WORKS OFFLINE · NO ADS · NO TRACKERS`.
- Voice per `docs/grease-pencil/README.md`.

## 12. Build order (each phase passes its checks on a real phone before the next)

**Phase 0 — Setup.** Repo, Android project, brand kit, theme, CI.

**Phase 1 — Offline core.** Jobs, areas, pairs, camera (before/after/ghost), review, preview, collage.
- ✅ 30 befores + 30 afters in airplane mode; app killed mid-shoot loses nothing.
- ✅ Ghost lines up with the live preview on 3 different phones. Shutter-to-ready < 500 ms.

**Phase 2 — Cloud gallery.** Worker, D1, R2, sign-in, Integrity, send/update pipeline, client page, Seen,
limits, global brake.
- ✅ Send offline → reconnect → uploads by itself; reboot mid-upload → resumes.
- ✅ Update after a crop uploads 0 photos. Link opens < 2 s on throttled 4G. No GPS in served images.
- ✅ Every limit in §7 has a test that proves the server refuses past it.

**Phase 3 — Pro + CRM.** Play Billing, inbound/outbound webhooks, API keys, Zapier/Make recipes.

**Phase 4 — Launch.** Onboarding, loop moment, accessibility, low-end devices, listing, closed test.

## 13. Testing
- **Unit (JVM):** pairing queue, manifest builder, limits, hashing, edge filter, collage layout, sync states.
- **Room (Robolectric):** DAOs and migrations.
- **Compose UI tests:** create job → shoot → review → preview.
- **Server:** Vitest on the Workers runtime, real D1/R2 locally; one test per limit and anti-reuse rule.
- **Device:** owner runs builds on their phones; field test with a real crew (sun, rain, no signal).
- CI runs all of the above on every push.

## 14. Risks
| Risk | Plan |
|---|---|
| CameraX quirks | Test Samsung, Pixel, Motorola, a cheap Android Go device early |
| Phone storage fills | Trim local originals after sending (setting); warn under 1 GB free |
| Battery savers kill uploads | WorkManager + foreground work; in-app tip if stalled |
| Free-tier abuse | §7 limits, Integrity, device recall, global brake |
| 14-day closed test | Recruit testers during Phase 2 |
| Scope creep | §1 no-bloat list |

## 15. Open
- Final app name (Play + domain availability).
