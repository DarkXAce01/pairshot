# Pairshot UI revamp — the spec

Owner: Grease Pencil Studio. Written by the design director. Builders build exactly this; where the
spec is silent, copy the nearest specified pattern and say so in your hand-off note.

Contents

1. Design direction
2. What is wrong today
3. The studio family (what we take from Swipe and Album)
4. Design tokens (light and dark)
5. Information architecture and navigation
6. Shared component library
7. Screen-by-screen spec
8. Templates for the final output
9. Onboarding
10. Data and logic changes the spec requires
11. Work packages
12. Director's review checklist

---

## 1. Design direction

**"The job sheet."** Pairshot looks like a well-printed work order: warm paper, black ink, hairline
rules, a serif for names and titles, a quiet sans for everything you read, and small monospaced
capitals for the facts (dates, counts, addresses, codes). Nothing floats, nothing glows: no shadows,
no gradients, no tinted Material surfaces, no cards stacked on cards. Photos are the only colour on
most screens. The grease-pencil red is used the way a foreman uses a red pencil on paper, only to
mark what matters: the one button that sends work to a client, the "Sent" and "Seen" marks, the
selected tab's stroke, the ghost outline in the camera, and the loop that draws itself once, when
the first link goes out. It is a paid business tool, so it is calm, dense enough to work fast in a
driveway, and predictable: every screen has one primary action, settings are a tidy list that opens
into small single-purpose pages, and developer-flavoured things (webhooks, keys, delivery logs,
server address) sit two levels down behind plain-language names. The creativity is in the details
you notice the second time: the pencil stroke under a tab, the hand-drawn circle round "Seen", the
client page thumbnails that carry the user's own business name and colour.

Rules that follow from this (builders: treat as hard rules):

- One accent-filled (red) button per screen at most. It is reserved for **Send link / Update link**
  and **Get Pro**. Every other primary action is an ink button.
- Red text is only for destructive confirmations and errors. Never red text plus a red button for
  different meanings in the same view, except the failed-send state (error line + "Try again" is ink).
- The loop **animates** in exactly one place: the Sent moment. Static pencil marks are allowed only
  where section 6 lists them.
- No Material `Card`/`ElevatedCard`, no `TopAppBar`, no `FilterChip`/`AssistChip`, no
  `ExtendedFloatingActionButton`, no `NavigationBar` in screen code. Use the `Ps*` components.
- Sentence case everywhere. Uppercase is only the mono eyebrow style, applied by the component.
- Copy follows `docs/grease-pencil/README.md`: plain words, short sentences, no exclamation marks,
  no "Oops", no fake urgency, no numbered decoration.

---

## 2. What is wrong today

Concrete findings from the current code (`android/app/src/main/java/com/greasepencil/pairshot/ui/**`).

Theme and foundations
- `ui/theme/Theme.kt` says it itself: "A placeholder look". Stock M3 `Typography()` with the serif
  dropped into three slots; body text is default Roboto; no spacing, shape or motion tokens; red is
  `primary`, so every button, switch, slider, chip, progress bar and link is red. The accent means
  nothing because it is everywhere.
- No app-level light/dark choice; `GreasePencil.red` reads the system setting directly.
- No shared components. Each screen hand-rolls rows, section labels, cards and badges
  (`SectionLabel` in Home, `Card`/`CodeBox`/`Meter`/`PlanBadge` private to `CloudScreen.kt`,
  `Badge` private to `JobScreen.kt`). Radii drift between 8, 10, 12 and 50 dp; button heights
  between 48, 52 and 56 dp; paddings between 10, 12, 16 and 24 dp.

Navigation
- Single stack with a gear icon. Business branding, which is half the product (it decides what the
  client sees), is buried in Settings. "Account & CRM" is reached through a full-width outlined
  button whose label is a sentence: "Account & CRM: sign in, usage, Zapier, webhooks".

Home (`home/HomeScreen.kt`)
- Stock `TopAppBar` + `ExtendedFloatingActionButton`. Job rows carry two competing status signals
  (an all-caps mono progress line and a status pill). Search appears and disappears as a full
  outlined field. The empty state is two centred lines of text with no way forward except the FAB.
- Low-space banner uses `errorContainer`, a colour that exists nowhere else.

Job (`job/JobScreen.kt`, `job/LinkCard.kt`)
- Five unrelated blocks stacked with equal weight: header links in red, two capture buttons, an
  elevated "Client link" card, a full-width "Preview the client page" outlined button, then pairs.
  The most valuable action (Send) is in the middle of the scroll and moves as content changes.
- The link card has up to six buttons (Text, Share, Copy, Update, Close, battery settings) in three
  different styles. Address and phone are bare red text with no affordance or 48 dp target.
- Edit is an icon, the gallery import and delete are in an overflow: two places for job actions.

Pair (`pair/PairScreen.kt`)
- A wrapping cloud of up to ten `AssistChip`s (Label, Make cover, Shown to client, Move earlier,
  Move later, Crop, Re-pair, Swap, Retake after…) with no grouping or hierarchy; frequent and rare,
  safe and risky all look the same. "Share as one image" opens a two-item dropdown with no preview.

Camera (`camera/CameraScreen.kt`)
- Functionally good. Chrome is stock M3 chips and an `OutlinedButton` tinted by hand on a dark
  background; "UNDO" is an 11 sp tap target; own private colours.

Settings (`settings/SettingsScreen.kt`)
- One long scroll of live-saving `OutlinedTextField`s, chip rows, a slider, a switch, storage maths,
  a cleanup chooser and a delete button, separated only by mono labels. Business identity, client
  page buttons, message template, camera defaults, uploads, storage and account are all one page.

Account & CRM (`cloud/CloudScreen.kt`, 810 lines)
- A wall of elevated cards: sign-in, account, usage, Pro pitch, CRM inbound address, webhooks (with
  an inline add form and the delivery log inside the same card), API keys, a debug "Developer" card,
  and a "Delete cloud data" card, all on one screen at the same visual weight. Raw URLs, signing
  secrets, `X-Pairshot-Signature`, "API keys (Zapier, Make)" face a pressure-washing owner the
  moment they open the page. The Pro pitch is four paragraphs inside a card titled "Connect your
  CRM"; the price button sits under them with no comparison of what changes.

Onboarding (`onboarding/OnboardingScreen.kt`)
- One form: name field, trade chips, Start. Nothing shows what the client will receive, so the
  first minute does not sell the product. No brand colour, no page style.

Output
- One client page look (`shared/gallery/gallery.html`); `business.accent` exists in the manifest
  and on the server but the app never sets it, so every business's page is Grease Pencil red.
- Collage export (`components/CollageExporter.kt`) has two layouts, a fixed paper background and no
  preview before sharing.

---

## 3. The studio family

What Swipe (`/home/jewel/projects/swipe`) and Album (`/home/jewel/projects/album`) share, and what
Pairshot keeps so the three read as one studio:

| Trait | In the siblings | In Pairshot |
|---|---|---|
| Paper and ink | Warm off-white `#F2EFE8`, near-black ink `#1C1A17`, dark side `#12110F` | Same family, section 4 |
| Type | Instrument Serif headlines, Geist body, Geist Mono small uppercase "eyebrows" (Swipe `Type.kt`) | Identical roles. Geist is added to Pairshot (see 4.2) |
| Primary action is ink, not red | Swipe `InkButton`: solid ink, paper text, 56 dp, radius 14 | `PsButton` Primary |
| Red is a mark, not a fill colour for the UI | Swipe `pencil` = marks and destructive; Album `Accent` = only "ready" and the one main action | Red = Send / Get Pro / marks |
| Hairlines, not shadows | `Hairline`, 1 dp borders, no elevation | Same |
| Tabs | Album `TabBar`: text tabs, selected one gets a short slanted pencil stroke | `PsTabBar`: icon + label, same pencil stroke |
| Top bar | Swipe `ScreenTopBar`: back, mono eyebrow, serif title | `PsTopBar` |
| Press feel | Swipe `Modifier.pressable`: scale to 0.97 with a soft spring | `Modifier.psPressable` |
| Screen change | Both: slide a sixth of the width + fade, 220–320 ms | Same, section 4.6 |
| Settings ending | `StudioLockup` + `PromiseLine` | Same, unchanged |
| Voice | Plain, warm, short | Same, a notch more matter-of-fact |

What Pairshot deliberately does **not** take: selectable in-app themes (Riso, Pop, Infrared),
paper grain/halftone textures, chunky sticker shadows, handwriting fonts, count-up animations.
Those are play; this is work. The app has exactly two looks: light and dark.

---

## 4. Design tokens

All tokens live in `ui/theme/`. Screens read them as `Ps.colors.*`, `Ps.type.*`, `Ps.space.*`,
`Ps.shape.*`, `Ps.motion.*` (a `Ps` object backed by `staticCompositionLocalOf`, like Swipe's
`Pixnix`). `PairshotTheme` also fills a Material 3 `ColorScheme`, `Typography` and `Shapes` from the
same tokens so any stock component that slips through still looks right.

### 4.1 Colour

Contrast figures were computed (WCAG 2.1 relative luminance). "Text" pairs meet AA 4.5:1; control
boundaries and graphic marks meet 3:1.

| Token | Light | Dark | Use |
|---|---|---|---|
| `paper` | `#F3F0EA` | `#141312` | Screen background |
| `surface` | `#FBF9F5` | `#1D1C1A` | Sheets, dialogs, grouped list blocks, bottom action bar, tab bar |
| `sunken` | `#E9E5DC` | `#262422` | Photo placeholders, code wells, input fill, meter track, unselected segment track |
| `ink` | `#1B1A18` | `#EDE9E2` | Main text, icons, primary button fill (15.3:1 on paper both themes) |
| `onInk` | `#F3F0EA` | `#141312` | Text on an ink fill |
| `inkSoft` | `#57534C` | `#B8B3A9` | Secondary text (6.7:1 / 8.9:1 on paper) |
| `inkFaint` | `#6B665E` | `#958F85` | Tertiary text, placeholders, mono eyebrows (5.0:1 / 5.8:1 on paper; 4.5:1 / 4.8:1 on sunken) |
| `hairline` | `#DCD6CA` | `#302E2A` | Dividers, decorative borders (not the only boundary of a control) |
| `outline` | `#847E73` | `#77726A` | Borders of inputs, secondary buttons, unselected chips (≥ 3.2:1 on every background) |
| `pencil` | `#D4432A` | `#E5553A` | Accent fill and marks (kit rule 4, exact values) |
| `onPencil` | `#FFFFFF` | `#1A0A06` | Text on a pencil fill (4.53:1 / 5.22:1) |
| `pencilText` | `#B3361F` | `#F0745A` | Red **text** on paper/surface/sunken: errors, destructive labels (≥ 4.8:1 / ≥ 5.4:1) |
| `pencilWash` | `#F6DDD5` | `#3B1B13` | Tinted background for "Sent" pill and warning banners (`pencilText` on it: 4.7:1 / 5.4:1) |
| `scrim` | `#000000` at 40 % | `#000000` at 60 % | Behind sheets and dialogs |
| `photoTag` | `#000000` at 55 % with `#FFFFFF` text | same | Tags over photos (BEFORE, AFTER, Cover) |

Camera chrome is always dark, in both themes (fixed `PsCameraColors`): background `#0E0E0D`, ink
`#EDE9E2` (16:1), muted `#A39E95` (7.2:1), hairline `#3A3835`, shutter `#FFFFFF`, ghost outline
`#E5553A`.

There is **no** green, amber or blue in the app chrome. Status is carried by words and icons, with
ink/pencil emphasis (section 6, `PsStatusPill`).

Material mapping (in `Theme.kt`): `primary = ink`, `onPrimary = onInk`, `secondary = inkSoft`,
`background = paper`, `surface = paper`, `surfaceVariant = sunken`, `onSurfaceVariant = inkSoft`,
`surfaceContainerLowest…Highest = surface` (Highest = `sunken`), `outline = outline`,
`outlineVariant = hairline`, `error = pencilText`, `onError = paper`, `errorContainer = pencilWash`,
`onErrorContainer = pencilText`, `inverseSurface = ink`, `inverseOnSurface = onInk`,
`scrim = #000000`. Nothing maps `primary` to red any more.

`res/values/colors.xml` `paper` / `paper_dark` (window background) change to `#F3F0EA` / `#141312`.

### 4.2 Type

Fonts in `res/font`: `instrument_serif.ttf`, `instrument_serif_italic.ttf`, `geist_mono.ttf`
(already there) plus **`geist.ttf`** (variable, 169 KB, SIL OFL), copied from
`/home/jewel/projects/swipe/app/src/main/res/font/geist.ttf`, with its licence copied from
`/home/jewel/projects/swipe/third_party/Geist-OFL.txt` to `docs/grease-pencil/fonts/Geist-OFL.txt`.
This is the only asset added; no new library. Load Geist and Geist Mono with
`FontVariation.Settings(FontVariation.weight(n))` exactly as Swipe's `Type.kt` does.

| Token (M3 slot) | Family | Size / line (sp) | Weight | Tracking | Use |
|---|---|---|---|---|---|
| `display` (displaySmall) | Instrument Serif | 40 / 44 | 400 | −0.015 em | Onboarding, Sent moment, Pro headline |
| `titleXL` (headlineLarge) | Instrument Serif | 32 / 36 | 400 | −0.01 em | Tab screen titles: Jobs, Brand, Settings |
| `titleL` (headlineMedium) | Instrument Serif | 26 / 30 | 400 | −0.01 em | Pushed-screen titles, client name on Job |
| `titleM` (headlineSmall) | Instrument Serif | 22 / 28 | 400 | −0.005 em | Sheet and dialog titles, empty-state titles |
| `heading` (titleLarge) | Geist | 18 / 24 | 600 | −0.01 em | In-page headings |
| `rowTitle` (titleMedium) | Geist | 16 / 22 | 500 | −0.005 em | List row titles, job names |
| `rowTitleS` (titleSmall) | Geist | 14 / 20 | 500 | 0 | Small headings, pair labels |
| `body` (bodyLarge) | Geist | 16 / 24 | 400 | 0 | Paragraphs, input text |
| `bodyS` (bodyMedium) | Geist | 14 / 20 | 400 | 0 | Row supporting text |
| `caption` (bodySmall) | Geist | 13 / 18 | 400 | 0 | Helper text, footnotes |
| `button` (labelLarge) | Geist | 15 / 20 | 600 | −0.005 em | Buttons |
| `chip` (labelMedium) | Geist | 13 / 16 | 500 | 0 | Chips, pills, tab labels |
| `eyebrow` (labelSmall) | Geist Mono | 11 / 14 | 500 | +0.08 em, uppercase | Section headers, eyebrows, photo tags |
| `mono` | Geist Mono | 13 / 18 | 400 | 0 | Addresses, keys, codes, counts in rows |

Rules: nothing readable is set below 13 sp; 11 sp exists only as uppercase mono. Numbers in rows use
`fontFeatureSettings = "tnum"`. The serif is never bold and never used below 22 sp. All text must
survive Android font scale 1.3 without clipping and 2.0 without loss of function (rows grow in
height; nothing has a fixed text-box height).

Keep exporting `Serif`, `Mono` and `studioFonts` from `Theme.kt` (the brand kit and exporter use them);
add `Sans`.

### 4.3 Spacing

4-point scale, named: `xxs 4`, `xs 8`, `sm 12`, `md 16`, `lg 20`, `xl 24`, `xxl 32`, `xxxl 40`,
`huge 56` (dp).

- Screen side gutter: **20 dp**. Sheets: 24 dp.
- Between a section header and its block: 8. Between blocks/sections: 32.
- Row padding: 16 vertical between text and row edge is achieved with min heights, not padding
  maths: single-line row 56 dp min, two-line row 72 dp min, with 20 dp side padding inside a block.
- Bottom action bar: 12 top, 12 bottom (+ navigation bar inset), 20 sides, 12 between buttons.

### 4.4 Shape

| Token | Radius | Use |
|---|---|---|
| `xs` | 6 dp | Photo tags, code wells |
| `sm` | 10 dp | Thumbnails, pair tiles, template thumbs' inner blocks |
| `md` | 14 dp | Buttons, inputs, list blocks, banners |
| `lg` | 20 dp | Sheet top corners, dialogs, hero preview |
| `pill` | 50 % | Status pills, chips, segmented control, avatars |

### 4.5 Elevation, borders, icons

- Elevation is **zero** everywhere. Separation is by `hairline` (1 dp) and by the paper/surface step.
- Grouped list blocks: `surface` fill, radius `md`, 1 dp `hairline` border, rows divided by a 1 dp
  `hairline` inset 20 dp from the left.
- The tab bar and the bottom action bar have a 1 dp `hairline` on their top edge and a `surface` fill.
- Focus (keyboard/switch access): 2 dp `ink` outline offset 2 dp.
- Icons: `androidx.compose.material.icons.Icons.Outlined.*` (the extended set is already a
  dependency), 24 dp, tinted `ink`; 20 dp inside buttons and rows' trailing slots; `inkSoft` for
  decorative leading icons. Filled icons only for: the Cover star, the shutter. No emoji, no custom
  icon font. Chevron for navigation rows: `Icons.AutoMirrored.Outlined.KeyboardArrowRight`, `inkFaint`.

### 4.6 Motion

| Token | Value | Use |
|---|---|---|
| `press` | spring, damping 0.55, medium stiffness, scale to 0.97 | Every pressable (`psPressable`) |
| `fast` | 120 ms | Colour/alpha changes, pill and chip selection |
| `standard` | 220 ms, FastOutSlowIn | Content change inside a screen (`AnimatedContent`, expanding rows) |
| `screen` | 300 ms slide by 1/6 width + 220 ms fade in, 160 ms fade out | NavHost push and pop (mirrored on pop) |
| `tab` | 160 ms cross-fade, no slide | Switching bottom tabs |
| `stroke` | 320 ms | Pencil stroke under the selected tab drawing in |
| `loop` | `GreasePencil.DRAW_MILLIS` (650 ms) + 250 ms, as `SentMoment` does today | The Sent moment only |

If the system animator scale is 0 (`Settings.Global.ANIMATOR_DURATION_SCALE`), all custom animation
is skipped and end states are shown (the existing `SentMoment` check becomes a shared
`rememberReducedMotion()` helper).

### 4.7 System bars and insets

`enableEdgeToEdge()` stays. Status and navigation bar icon colour follows the app theme (not the
system's) when the user forces light or dark. Every screen pads for `WindowInsets.safeDrawing`; the
tab bar and bottom action bar consume the navigation bar inset; sheets use `imePadding()`.

### 4.8 Appearance setting

New: **Appearance = System (default) / Light / Dark**, stored in `SettingsStore` (`theme_mode`).
`MainActivity` passes the resolved value to `PairshotTheme(dark = …)`. App code uses
`Ps.colors.pencil`, never `GreasePencil.red` (which reads the system setting); the kit files in
`brand/` are not edited.

---

## 5. Information architecture and navigation

### 5.1 Model

**Three bottom tabs: Jobs · Brand · Settings.** The tab bar shows only on these three root screens.
Everything else is pushed full-screen over it and has a back arrow.

- **Jobs** is the start destination and the daily screen (capture speed: open app → job → camera).
- **Brand** is "what your client sees": business identity, colour, page style, the buttons on the
  page, the message. It is its own tab because it is the product's output and the reason to pay.
- **Settings** is a short grouped list. Every row opens one small page that does one thing.

Why not a drawer or a gear icon: two of the three areas were invisible behind a gear. Why not four
or five tabs: there is no fourth thing a crew needs daily; a "Links" tab would duplicate job status.

### 5.2 Route map

```
OnboardingRoute (first run only)
Tabs
├─ HomeRoute  "Jobs"
│   ├─ NewJobRoute(editJobId?)
│   └─ JobRoute(jobId)
│       ├─ CameraRoute(jobId, after, pairId?)
│       ├─ PairRoute(pairId)            (+ sheets: Label, More, Re-pair, Share image; Crop editor)
│       └─ PreviewRoute(jobId)          (+ sheet: Page style)
├─ BrandRoute  "Brand"
│   └─ MessageRoute                     (message editor)
└─ SettingsRoute  "Settings"
    ├─ AccountGraph  (one CloudViewModel shared by every screen in this graph)
    │   ├─ AccountRoute                 (sign in / signed-in summary, usage, sign out, delete cloud data)
    │   ├─ ProRoute                     (plan comparison, buy, restore)
    │   ├─ ConnectionsRoute             (hub: three rows + setup guide)
    │   │   ├─ IncomingJobsRoute        (CRM inbound address, sync, new address)
    │   │   ├─ CrmUpdatesRoute          (outbound webhooks: list, add, test, remove)
    │   │   │   └─ DeliveryLogRoute     (recent deliveries)
    │   │   └─ ApiKeysRoute             (keys: list, create, delete)
    │   └─ DeveloperRoute               (debug builds only: server address, pretend Pro)
    ├─ CameraSettingsRoute              (ghost defaults)
    ├─ StorageRoute                     (Wi-Fi only, phone storage, free up space)
    └─ AboutRoute                       (help links, licences, what is stored where)
```

`CloudRoute` is removed. `SettingsRoute` remains but is now a tab root. Notification deep link
(`openJob`) behaviour is unchanged: `JobRoute` over `HomeRoute`.

### 5.3 Where every existing feature lives

| Feature today | New home | Disclosure level |
|---|---|---|
| Job list, search, today/recent, low-space banner | Jobs tab | 0 |
| New / edit job | Jobs → New job; Job → ⋮ → Edit job | 1 |
| Capture before/after, import from gallery | Job screen (import under ⋮) | 1 |
| Pair review: label, crop, cover, hide | Pair screen tool row | 2 |
| Pair: move, re-pair, swap, retake, delete | Pair → "More" sheet | 3 |
| Share as one image | Pair → Share image sheet (templates, section 8b) | 3 |
| Client page preview | Job bottom bar → Preview | 2 |
| Per-job page style (new) | Preview → Style | 3 |
| Send / update / close link, text/share/copy, seen, stall tip, send over mobile data | Job screen: bottom bar (Send/Update) + link panel | 1 |
| **Sign-in** | First Send (sheet, as today) **and** Settings → Account | at the moment of need |
| Business name, trade | Brand tab | 0 |
| Business accent colour (new), client page template (new) | Brand tab → Look | 0 |
| Call / review / booking buttons | Brand tab → On the page | 0 (edit in a sheet) |
| **Message template** | Brand tab → Message → `MessageRoute` | 1 |
| Usage this month, plan badge | Settings → Account | 1 |
| **Pro** purchase, restore | Settings → Pairshot Pro (`ProRoute`); also linked from locked Connections, free watermark note, usage-full note | 1 |
| **CRM inbound address**, sync now, new address, setup guide | Settings → Connections → Jobs from your CRM | 2 |
| **Webhooks** (add, test, remove, signing secret reveal) | Settings → Connections → Updates to your CRM | 2 |
| **Delivery log** | … → Updates to your CRM → Recent deliveries | 3 |
| **API keys** (create, delete, reveal once) | Settings → Connections → API keys | 2 |
| **Ghost defaults** (mode, strength) | Settings → Camera | 1 (also adjustable live in the camera, as today) |
| **Wi-Fi only** | Settings → Uploads & storage | 1 |
| **Storage / cleanup** (usage, free up space) | Settings → Uploads & storage | 1 |
| **Delete cloud data** | Settings → Account → last block "Delete cloud data…" | 2, behind a confirm dialog |
| Sign out | Settings → Account | 1 |
| **Server address, pretend Pro (debug only)** | Settings → Developer, row rendered only when `BuildConfig.DEBUG` | 1, debug builds only |
| Appearance (new) | Settings tab, inline segmented control | 0 |
| Studio lockup + promise line | End of Settings tab | 0 |

Words the user sees: "Connections" (not "integrations" or "CRM & API"), "Jobs from your CRM" (not
"inbound webhook"), "Updates to your CRM" (not "webhooks"; the word "webhook" appears once, as the
field label "Webhook address", because that is what Zapier calls it), "API keys" (kept; it is the
term the user has to find in Zapier/Make).

### 5.4 Shared ViewModel for the account graph

`CloudViewModel` stays as it is. Screens inside `AccountGraph` obtain the same instance with
`hiltViewModel(remember(entry) { nav.getBackStackEntry(AccountGraph) })`. Each screen collects
`vm.messages` into its own snackbar host (only the visible screen is composed, so the single-consumer
channel is safe) and renders `extras.reveal` / confirm dialogs itself via the shared
`PsRevealDialog` / `PsConfirmDialog`. The Settings tab root does **not** create `CloudViewModel`
(that would hit the network on every tab switch); it reads `CloudSession.state` through
`SettingsViewModel` (section 10).

---

## 6. Shared component library

All in `ui/components/ps/` (one file per group). Every component takes a `modifier`, has a
`@Preview` in light and dark, exposes correct semantics, and has a minimum touch target of 48 dp.
Built in Work Package 1, before any screen (except `PsTemplateThumb`, which arrives with the templates in WP5).

| Component | Look | Behaviour |
|---|---|---|
| `PsScaffold(topBar, bottomBar, snackbarHost, content)` | `paper` background, handles insets | Thin wrapper over a `Column`/`Box`; no M3 `Scaffold` colours |
| `PsTopBar(title, onBack?, eyebrow?, actions)` | 56 dp min; back arrow (48 dp) at 8 dp from edge; optional mono eyebrow above a `titleL` serif title, one line, ellipsis | Pushed screens. Title is a `heading()` for TalkBack. Back is "Back" |
| `PsTabHeader(title, eyebrow?, actions)` | 20 dp gutter, eyebrow then `titleXL` serif, 24 dp top, 12 dp bottom | Root tab screens (no back arrow). Scrolls with content |
| `PsTabBar(current, onSelect)` | 64 dp + nav inset, `surface`, top hairline. Three items: outlined icon 24 dp over `chip` label. Selected: `ink` icon and label + `PsPencilStroke` under the label. Unselected: `inkFaint` | `Role.Tab`, `selected` semantics; stroke draws in over `stroke` ms |
| `PsPencilStroke` | 26 × 6 dp slanted waxy line in `pencil`, round caps (Album's tab stroke) | Static or draw-in |
| `PsButton(text, onClick, variant, icon?, enabled, busy, fullWidth)` | Height 52 dp, radius `md`, `button` type. **Primary**: `ink` fill, `onInk` text. **Accent**: `pencil` fill, `onPencil` text. **Secondary**: transparent, 1 dp `outline` border, `ink` text. **Quiet**: no border, `ink` text, 48 dp. **Danger**: Quiet with `pencilText`. Disabled: 40 % alpha. `busy` swaps the icon for an 18 dp spinner and disables | `psPressable`. Accent is reserved (section 1) |
| `PsIconButton(icon, label, onClick)` | 48 dp target, 24 dp icon, no background | `label` is the required content description |
| `PsFab(text, icon, onClick)` | Ink pill, 56 dp high, `onInk` text + icon, no shadow, 16 dp above the tab bar, 20 dp from the end | Jobs only: "New job" |
| `PsBottomBar { … }` | `surface`, top hairline, padding per 4.3, buttons share width equally | Pinned above the navigation bar on pushed screens |
| `PsSectionHeader(text, trailing?)` | Mono `eyebrow` in `inkFaint`, 20 dp gutter, 8 dp below | `heading()` semantics |
| `PsListBlock { rows }` | The grouped block of 4.5 | Lays out rows with inset dividers |
| `PsListRow(title, supporting?, leading?, value?, trailing = Chevron, onClick?)` | `rowTitle` + optional `bodyS` in `inkSoft`; optional `value` right-aligned in `bodyS` `inkSoft` (e.g. "Not set", "Outline"); chevron | Whole row clickable, merged semantics, `Role.Button` |
| `PsSwitchRow(title, supporting?, checked, onChange)` | Row with a switch: track `sunken`/`ink`, thumb `surface`/`onInk` (never red) | `toggleable`, `Role.Switch` on the row |
| `PsValueRow(title, value)` | Non-clickable row, value in `mono` | For facts (version, plan limits) |
| `PsTextField(value, onChange, label, helper?, error?, …)` | Label above in `caption` `inkSoft`; field 52 dp min, `sunken` fill, radius `md`, 1 dp `outline` border, 2 dp `ink` when focused, `pencilText` border + helper on error | Wraps `BasicTextField`; cursor `ink`; supports keyboard options, single/multi-line |
| `PsSearchField` | `PsTextField` without label, leading search icon, trailing clear | Jobs search |
| `PsChip(text, selected, onClick, leadingIcon?)` | Pill, 36 dp visual in a 48 dp target. Selected: `ink` fill, `onInk` text. Unselected: 1 dp `outline`, `ink` text | `Role.RadioButton` or `Checkbox` by parameter; `PsChipRow` (wrap) and `PsChipScroller` (LazyRow) helpers. `onDark = true` variant for the camera |
| `PsSegmented(options, selected, onSelect)` | Pill track `sunken`, selected segment `ink` fill / `onInk` text, 44 dp high in 48 dp target | 2–3 options (Appearance, ghost mode) |
| `PsSlider` | 4 dp track `sunken`, active `ink`, 24 dp thumb `ink` | Wraps M3 `Slider` with colours; content description required |
| `PsStatusPill(status)` | Pill, `chip` type, 26 dp high. Upcoming: 1 dp `outline`, `inkSoft` text. In progress: `sunken` fill, `ink`. Done: `ink` fill, `onInk`. Sent: `pencilWash` fill, `pencilText`. Seen: `pencil` fill, `onPencil` | Content description "Status: Seen" |
| `PsPlanPill(pro)` | "Free" = outline style; "Pro" = ink fill | — |
| `PsTag(text)` | Mono `eyebrow`, white on `photoTag`, radius `xs` | Over photos |
| `PsBanner(title, body, action?, tone)` | Radius `md`, 16 dp padding. `Neutral`: `surface` + hairline. `Warning`: `pencilWash`, title in `pencilText` | Low storage, limit reached |
| `PsMeter(label, value, spoken, fraction, full)` | Label + mono value, 6 dp track `sunken`, fill `ink` (or `pencil` when `full`) | Keeps today's `clearAndSetSemantics` behaviour |
| `PsCodeWell(text, onCopy)` | `sunken`, radius `xs`, `mono` text, selectable, wraps; trailing copy icon button | Addresses, keys, secrets |
| `PsEmptyState(art, title, body, action?)` | Centred; hairline line-art (see below), `titleM` serif title, `bodyS` `inkSoft`, optional `PsButton` | — |
| `PsPairArt` | Line art: two rounded rectangles side by side, left solid hairline tagged BEFORE, right dashed tagged AFTER | Empty Jobs / empty Job |
| `PsSheet(title, onDismiss) { … }` | `ModalBottomSheet`, `surface`, top radius `lg`, drag handle `hairline`, `titleM` serif title, 24 dp gutter, `imePadding` | All sheets |
| `PsConfirmDialog(title, text, confirm, destructive, onConfirm, onDismiss)` | `surface`, radius `lg`, `titleM` title, `body` text; buttons right-aligned Quiet; destructive confirm in `pencilText` | Replaces every ad-hoc `AlertDialog` |
| `PsRevealDialog(title, secret, onCopyClose, onSaved)` | Dialog with `PsCodeWell` | "Copy this now" (keys, signing secrets) |
| `PsSnackbarHost` | Ink pill-ish bar (radius `md`), `onInk` text | One style for all snackbars |
| `PsBusyBar(busy)` | 2 dp indeterminate line in `ink` under the top bar; reserves its height | Account graph screens |
| `PsAvatar(letter)` | 44 dp circle, `sunken`, serif letter | Account row |
| `PsPhotoThumb(file, rotation, size)` | Radius `sm`, `sunken` placeholder | Wraps `PhotoImage` |
| `PsPairTile(pair, onClick)` | Two halves with 2 dp gap, radius `sm`; "Needs after" half is `sunken` with a dashed `outline` border and mono text; Cover star and Hidden eye as `PsTag`-style badges | From `JobScreen.PairTile` |
| `PsLoopMark(content)` | Static `Modifier.circled()` in `pencil` round its content | Allowed uses only: onboarding wordmark, "Seen" in the link panel, "Pro" on the Pro page when owned |
| `PsTemplateThumb(template, accent, businessName, …)` | Miniature of a client page template (section 8c) | Brand, onboarding, Preview style sheet |
| `PsSwatch(colour, selected, label)` | 36 dp circle in 48 dp target; selected: 2 dp `ink` ring with 3 dp gap (ends inside the target). Laid out in `PsSwatchRow` (wrapping, 4 dp gaps both ways) | Accent picker; label is spoken |
| `PsSelectableFrame(selected) { … }` | Radius `md`; 1 dp `hairline` border, or 2 dp `ink` border plus a 22 dp `pencil` circle with a white check at top-right when selected | Template and collage thumbnails. `Role.RadioButton` |
| `GoogleSignInButton` (kept, restyled) | Secondary-style button with the account icon, 56 dp | From `cloud/SignIn.kt` |
| `Modifier.psPressable`, `rememberReducedMotion()` | — | Section 4.6 |

Brand kit components (`GreasePencilLoop`, `circled`, `PromiseLine`, `StudioLockup`) are used as is.

---

## 7. Screen-by-screen spec

Conventions: wireframes are 360 dp wide sketches; `[ ]` is a button, `( )` a chip/pill, `›` a
chevron, `▢` a photo. Existing copy that is not mentioned stays word for word. Existing semantics
(`liveRegion`, `heading`, merged descendants, content descriptions) must be kept or improved.

### 7.0 App shell

`AppNav` hosts a `NavHost` inside a `Column`; `PsTabBar` is shown when the current destination is
`HomeRoute`, `BrandRoute` or `SettingsRoute` (animated in/out with a 160 ms fade, no slide).
Selecting a tab uses `popUpTo(HomeRoute) { saveState = true }`, `launchSingleTop`, `restoreState`.
System back on Brand or Settings returns to Jobs; on Jobs it leaves the app.

### 7.1 Jobs (Home)

Purpose: find today's job and get into it; start a new one.

```
┌──────────────────────────────────────┐
│ SPARKLE WASH                    🔍   │  eyebrow = business name (omitted if blank)
│ Jobs                                 │  titleXL serif
│                                      │
│ ┌ Phone storage is nearly full ────┐ │  PsBanner Warning (only when low)
│ │ Only 412 MB left. New photos…    │ │
│ │ [Free up space]                  │ │
│ └──────────────────────────────────┘ │
│ TODAY                                │  PsSectionHeader
│ ┌──────────────────────────────────┐ │
│ │ ▢  Dana Whitfield     (In progress)│
│ │    House wash · 12 Oak St        │ │
│ │    2 OF 5 AFTERS                 │ │  mono, only while in progress
│ ├──────────────────────────────────┤ │
│ │ ▢  Marcus Lee              (Seen)│ │
│ │    Driveway · 4 Elm Ct           │ │
│ └──────────────────────────────────┘ │
│ EARLIER                              │
│ ┌──────────────────────────────────┐ │
│ │ ▢  …                             │ │
│ └──────────────────────────────────┘ │
│                        [ + New job ] │  PsFab
├──────────────────────────────────────┤
│   Jobs        Brand       Settings   │  PsTabBar (stroke under Jobs)
└──────────────────────────────────────┘
```

- Header: `PsTabHeader("Jobs", eyebrow = businessName)`. Search icon appears when `totalJobs > 3`;
  tapping it swaps the header for a `PsSearchField` ("Search client, address, service") with a
  "Cancel" quiet button; clearing restores the header. Uses the existing `vm.query`.
- Job row (`JobRow` in `home/`): 56 dp `PsPhotoThumb`, `rowTitle` client name, `bodyS` `inkSoft`
  "service · address" (one line), `PsStatusPill` at the end. Third line (mono `eyebrow`) only when
  status is In progress: "2 OF 5 AFTERS". Rows live in one `PsListBlock` per section; min 76 dp.
- Section names: "Today", "Earlier" (was RECENT / OTHER JOBS; "Recent" when there is no Today).
- Low space: `PsBanner(Warning)`; action "Free up space" opens `StorageRoute`.
- Empty (no jobs): `PsEmptyState(PsPairArt, "No jobs yet", "Add a job, shoot the befores, then the
  afters. They pair up on their own.", action = Primary "New job")`. The FAB hides in this state.
- No search results: "Nothing matches "{query}"." in `bodyS` `inkSoft`, 24 dp top.
- Loading: nothing is drawn until `state.loaded` (as today); no spinner (Room answers in a frame).
- Accessibility: each row is one merged node: "Dana Whitfield, House wash, 12 Oak St, status In
  progress, 2 of 5 afters". FAB label "New job".
- Logic: unchanged. `statusOf`, `progressText` stay public where they are.

### 7.2 New job / Edit job

Purpose: the fastest possible form. Only the name is required.

```
┌──────────────────────────────────────┐
│ ←  New job                           │
│                                      │
│ Client name                          │
│ [ Dana Whitfield                   ] │
│ Service                    optional  │
│ [ House wash                       ] │
│ Address                    optional  │
│ [                                  ] │
│ Client phone               optional  │
│ [                                  ] │
│ WHEN                                 │
│ (Today) (Tomorrow) (No date)         │  existing day options, PsChip radio
│                                      │
├──────────────────────────────────────┤
│ [          Create job              ] │  PsBottomBar, Primary; "Save" when editing
└──────────────────────────────────────┘
```

- `PsTextField`s; "optional" is a right-aligned `caption` in `inkFaint` on the label line, not in
  the label text. Name field autofocuses on New, capitalises words; phone uses the phone keyboard.
- The button sits in a `PsBottomBar` with `imePadding()` so it stays above the keyboard.
- Edit: title "Edit job"; when the job's link has been sent the existing lock rules/messages apply
  unchanged.
- Helper under phone: "Lets you text the link straight to them."
- Logic: `NewJobViewModel` untouched.

### 7.3 Job

Purpose: run one job from first before to sent link. The screen always answers "what's next?".

```
┌──────────────────────────────────────┐
│ ←  Dana Whitfield                 ⋮  │  PsTopBar; ⋮ = Edit job, Add photos from gallery, Delete job
│ House wash · 12 Oak St       📍  📞  │  bodyS inkSoft + icon buttons (Map, Call) when present
│                                      │
│ ┌ CLIENT LINK ─────────────── (Seen)┐│  Link panel (only once a link exists / is sending / failed)
│ │ pairshot…/g/x7Kq2                 ││  mono, one line
│ │ Opened 2 times, first 3 h ago     ││
│ │ [ Text ] [ Share ] [ Copy ]    ⋮  ││  ⋮ = Close link…
│ └───────────────────────────────────┘│
│ [ 📷 Before ]   [ 📷 After · 3 left ] │  capture row
│                                      │
│ DRIVEWAY                     2/3   ⋮ │  PsSectionHeader + count + label menu
│ ▢▢  ▢▢                               │  PsPairTile grid, 2 columns, 8 dp gap
│ ▢┄                                   │
│ NO LABEL                     1/1     │
│ ▢▢                                   │
├──────────────────────────────────────┤
│ [ Preview ]        [ Send link ]     │  PsBottomBar
└──────────────────────────────────────┘
```

- Header: `PsTopBar(title = clientName)`. One overflow menu holds Edit job, Add photos from gallery,
  Delete job (the separate edit icon is removed). Under it a meta row: "service · address" and, at
  the end, `PsIconButton(Map, "Open the address in maps")` and `PsIconButton(Call, "Call the
  client")` when those fields exist. No red link text.
- **Capture row**: two 56 dp buttons, equal width. The primary flips to what is next: when
  `pending > 0` After is **Primary** ("After · 3 left") and Before is Secondary; otherwise Before is
  Primary and After is Secondary and disabled. Both keep the camera icon.
- **Pairs**: per label, `PsSectionHeader(label, trailing = "2/3" mono + ⋮)`; "No label" group has
  no menu. `PsPairTile` grid as today (two per row, 3:2).
- **Bottom bar** (the single place for the money action):

  | State | Left | Right |
  |---|---|---|
  | No finished pair | (bar hidden) | |
  | Finished pairs, no link (or last link closed/expired) | Secondary "Preview" | **Accent** "Send link" ("Send a new link" after closed/expired) |
  | Queued / sending | Secondary "Preview" | Accent, `busy`, "Sending…" (disabled) |
  | Live, nothing changed | Secondary "Preview" (full width) | — |
  | Live, changed since sending | Secondary "Preview" | **Accent** "Update link" |
  | Failed | Secondary "Preview" | Primary "Try again" |

- **Link panel** (`job/LinkPanel.kt`, replaces `LinkCard`): a `surface` block with hairline, radius
  `md`, shown directly under the meta row whenever `gallery != null`. Content by state, keeping
  every state and string `LinkCard` has today:
  - Queued/sending: spinner + "Sending…" / "Updating the link…" / "Waiting for Wi-Fi. It'll go by
    itself." / "Waiting for signal. It'll go by itself."; server note if any; Quiet "Send now using
    mobile data" (Wi-Fi-only case); the stall tip + Quiet "Open battery settings". `liveRegion` polite.
  - Failed: title "Not sent" / "Not updated", error in `pencilText`, Quiet "Share the old link" when
    there is one. (Try again is in the bottom bar.)
  - Live: eyebrow "CLIENT LINK" with `PsStatusPill` Sent or Seen at the end (when Seen, the pill is
    wrapped in `PsLoopMark`: the hand-drawn circle, static); the URL in `mono` (one line, middle
    ellipsis); the existing seen/expiry line; a row of three Secondary 48 dp buttons Text (only with
    a client phone) / Share / Copy, and an overflow with "Close link…". When changed: `caption`
    "You've changed things since sending."
  - Closed/expired: one line "You closed the last link." / "The last link expired." in `inkSoft`.
  - Before any link exists the panel is absent; the explanation moves to the empty/first-run hints
    below.
- Hints (one `bodyS` `inkSoft` line above the pairs, never a card): no photos → "No photos yet. Tap
  Before and shoot. Labels like "Driveway" are optional."; photos but no finished pair → "Finish a
  before and after to send a link."; first finished pair and no link yet → "Send makes a page with
  these before and afters for your client. Their name isn't on it."
- Empty job: `PsPairArt` above the first hint.
- Dialogs: all through `PsConfirmDialog` with today's copy. Rename label uses a `PsSheet` with a
  `PsTextField` and Primary "Save".
- Sign-in sheet, notification permission, Sent moment, share: behaviour unchanged.
- Accessibility: bottom-bar buttons are reachable after the list in traversal order; pair tiles say
  "Driveway, before and after" / "Driveway, needs after", plus "cover", "hidden from client".
- Logic: `JobViewModel` untouched. `looksStalled`, `STALL_MILLIS`, `LinkState`, `LinkActions` keep
  their names and package (tests depend on them).

### 7.4 Camera

Purpose unchanged; only the chrome is restyled. Always dark (`PsCameraColors`).

```
┌──────────────────────────────────────┐
│ ✕  AFTER              Driveway · 3 left│ mono eyebrow + bodyS muted
│                                      │
│            live preview              │
│        (ghost / red outline)         │
│                          (1.0×)      │
│                               [Peek] │
│ (Outline)(Photo)(Off)  ──────●────   │  PsSegmented onDark + PsSlider
│ (No label)(Driveway)(Patio)(+ Label) │  PsChipScroller onDark
│  ▢                ◯            [Done]│
│  Undo                         · 4    │
└──────────────────────────────────────┘
```

- Replace `FilterChip`/`AssistChip`/`OutlinedButton` with `PsSegmented(onDark)`, `PsChip(onDark)`,
  `PsButton(Secondary, onDark)`; private colour constants move to `PsCameraColors`.
- "Undo" becomes a 48 dp target: the thumbnail and the word are one button labelled "Undo last
  photo".
- Shutter stays 76 dp white ring; content description "Take photo"; disabled state muted.
- Permission denied: centred `titleM` "Pairshot needs the camera", `bodyS` "It's used only while
  this screen is open, for your before and after photos.", Primary-on-dark "Open settings", Quiet
  "Go back".
- All done: `titleM` "Every before has its after.", button "Back to the job".
- Label picker sheet → `PsSheet("Add a label")` with `PsChipRow` and a `PsTextField` + "Add".
- Do not touch: `CameraViewModel`, CameraX wiring, `GhostLoader`, gestures, `VolumeKeys`.

### 7.5 Pair

Purpose: check one pair, fix it, optionally share it as an image.

```
┌──────────────────────────────────────┐
│ ←  Driveway                          │
│ ┌──────────────────────────────────┐ │
│ │  BEFORE        ‹›          AFTER │ │  BeforeAfterSlider (restyled knob/tags)
│ └──────────────────────────────────┘ │
│   ◇        ⌗         ★        👁     │  tool row: 4 labelled tools
│  Label    Crop     Cover    Shown    │
│                                      │
│ ┌──────────────────────────────────┐ │
│ │ Retake the after               › │ │  PsListBlock, the common fix
│ │ More                           › │ │  opens the More sheet
│ └──────────────────────────────────┘ │
├──────────────────────────────────────┤
│ [        Share as one image        ] │  Secondary, PsBottomBar
└──────────────────────────────────────┘
```

- **Tool row**: four equal columns, each a 64 dp-high pressable with a 24 dp icon and a `chip`
  label: **Label** (value shown as the screen title), **Crop** ("Cropped" when cropped), **Cover**
  (toggle; filled star + "Cover" in `ink` when on, outline star + "Make cover" in `inkSoft` when
  off), **Shown / Hidden** (toggle; eye / eye-off). Toggles expose `stateDescription`.
- **More sheet** (`PsSheet("More")`, list rows): Move earlier, Move later (shown when the label has
  more than one pair; disabled at the ends), Wrong after? Re-pair (when the job has other pairs),
  Swap before and after, Pick the after from your gallery, and last, separated by a hairline,
  "Delete pair…" in `pencilText` (opens the confirm dialog). The top-bar delete icon is removed.
- Needs-after state: the slider shows the before with a dashed empty half; the bottom bar becomes
  Primary "Shoot the after"; the list block shows "Pick the after from your gallery"; Share is
  hidden.
- Share: opens the **Share image sheet** (section 8b).
- Crop editor (`CropEditor.kt`): stays full-screen dark; buttons become `PsButton(Secondary/Primary,
  onDark)`; title `titleM`. Re-pair sheet and Label sheet move onto `PsSheet`, `PsChip`,
  `PsTextField`; copy unchanged.
- Logic: `PairViewModel` untouched except the export call (section 8b).

### 7.6 Preview

Purpose: the exact client page, plus the place to change its style for this job.

```
┌──────────────────────────────────────┐
│ ←  Client preview            [Style] │  Quiet button with palette icon
│ This is the page your client gets.   │  caption, inkSoft
│ ┌──────────────────────────────────┐ │
│ │            WebView               │ │  hairline top border
│ │                                  │ │
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

- "Style" opens `PsSheet("Page style")`: a horizontal row of the four `PsTemplateThumb`s (each in
  a `PsSelectableFrame`, name under it), the first entry being "Your default ({name})". Choosing
  one stores the per-job override (section 10) and the WebView re-renders behind the sheet at once.
  Footer `caption`: "Changes this job only. Your default is in Brand." If the link is live:
  "Tap Update link on the job to show the client."
- The WebView's own light/dark follows the system as a client would see it; the frame round it
  follows the app theme.
- Loading: `sunken` rectangle until `html` is ready. Nothing to show (no finished pairs): 
  `PsEmptyState("Nothing to show yet", "Finish a before and after and it appears here.")`.
- Logic: `PreviewViewModel` gains the template input (section 10); the WebView code is unchanged.

### 7.7 Sent moment

Unchanged in behaviour and timing (`job/SentMoment.kt`). Restyle only: `paper` at 96 %, cover photo
radius `md`, the loop in `pencil`, the word "Sent." in `display` serif, and under it one `caption`
line in `inkSoft`: "Pick how to send it." It remains the app's single animated loop.

### 7.8 Sign-in sheet (first Send)

`PsSheet("Sign in to send your link")`, three `FactRow`s restyled (20 dp `inkSoft` icon + `bodyS`),
keeping today's facts; error line in `pencilText` with an error icon; `GoogleSignInButton`; Quiet
"Not now". No other change.

### 7.9 Brand (tab)

Purpose: decide what clients see, once, and see it while deciding.

```
┌──────────────────────────────────────┐
│ WHAT YOUR CLIENT SEES                │
│ Brand                                │
│ ┌──────────────────────────────────┐ │
│ │      ┌───────────────┐           │ │  hero: PsTemplateThumb, 168×240 dp, centred on a
│ │      │ Sparkle Wash  │           │ │  sunken panel, radius lg; live: name, colour, style
│ │      │ ▢▢ slider     │           │ │
│ │      │ [Leave review]│           │ │
│ │      └───────────────┘           │ │
│ └──────────────────────────────────┘ │
│ BUSINESS                             │
│ ┌──────────────────────────────────┐ │
│ │ Name              Sparkle Wash › │ │  sheet with PsTextField
│ │ Trade         Pressure washing › │ │  sheet with PsChipRow (radio)
│ └──────────────────────────────────┘ │
│ LOOK                                 │
│ Colour                               │
│ ● ● ● ● ● ● ● ●  (+)                 │  PsSwatch row; (+) = your own colour
│ Page style                           │
│ ┌────┐ ┌────┐ ┌────┐ ┌────┐          │  PsTemplateThumb 104×148 in PsSelectableFrame
│ │    │ │    │ │    │ │    │          │
│ └────┘ └────┘ └────┘ └────┘          │
│ Classic Showroom Bright Report       │
│ Warm paper and a serif. Suits most.  │  blurb of the selected style
│ ON THE PAGE                          │
│ ┌──────────────────────────────────┐ │
│ │ Call button          Not set   › │ │
│ │ Leave a review       Set       › │ │
│ │ Book again           Not set   › │ │
│ └──────────────────────────────────┘ │
│ Each button shows only if you fill it in.
│ MESSAGE                              │
│ ┌──────────────────────────────────┐ │
│ │ Hi Dana, here's your house wash  │ │  the template filled with example values
│ │ before & after: pairshot…      › │ │
│ └──────────────────────────────────┘ │
├──────────────────────────────────────┤
│   Jobs        Brand       Settings   │
└──────────────────────────────────────┘
```

- Everything saves immediately (as Settings does today); no Save button.
- Edit sheets (`PsSheet`): one `PsTextField` (name; phone with phone keyboard; review and booking
  with URL keyboard and helper lines "Paste your Google review link." / "Your booking page."),
  Primary "Done". Clearing a field and tapping Done turns the button off ("Not set").
- Trade sheet helper: "This only sets the label names we suggest. You can type your own any time."
- Colour and page style: section 8c.
- Free plan note under Look, `caption` `inkSoft`: "Free pages carry a small "Made with Pairshot"
  line. Pro removes it." with Quiet "See Pro".
- **Message editor** (`MessageRoute`): `PsTopBar("Message to the client")`; a multi-line
  `PsTextField`; under it three insert chips `{first_name}` `{service}` `{link}` (tap inserts at the
  cursor); a "Preview" block (`surface`, hairline) showing the message filled with "Dana", the
  trade's sample service and a sample link; Quiet "Reset to the standard message"; helper: "The link
  goes where {link} is. Leave it in." If `{link}` is missing, a `pencilText` helper says "Without
  {link} your client gets no link." (no blocking).
- Accessibility: swatches and thumbs are radio groups with spoken names ("Navy, selected");
  the hero is described as "Preview of your client page in Classic, Navy".
- Logic: reads/writes `SettingsStore` through a new `BrandViewModel` (section 10).

### 7.10 Settings (tab)

Purpose: a short map. No inputs on this screen except the Appearance control.

```
┌──────────────────────────────────────┐
│ PAIRSHOT 0.1.0                       │
│ Settings                             │
│ ┌──────────────────────────────────┐ │
│ │ (D) dana@sparklewash.com  (Free)›│ │  or: "Sign in" / "Only needed to send links."
│ │ Pairshot Pro      One payment  › │ │  or: "Pro · Thank you" (no price)
│ └──────────────────────────────────┘ │
│ WORK                                 │
│ ┌──────────────────────────────────┐ │
│ │ Camera               Outline   › │ │
│ │ Uploads & storage    1.2 GB    › │ │
│ │ Connections          Pro       › │ │  value "Pro" pill when locked; "2 on" style summary when Pro
│ └──────────────────────────────────┘ │
│ APP                                  │
│ ┌──────────────────────────────────┐ │
│ │ Appearance  [System|Light|Dark]  │ │  PsSegmented inline
│ │ Help & about                   › │ │
│ │ Developer                      › │ │  debug builds only
│ └──────────────────────────────────┘ │
│                                      │
│            Grease Pencil             │  StudioLockup
│  PAIRSHOT 0.1.0 · WORKS OFFLINE ·    │  PromiseLine (unchanged promises)
│       NO ADS · NO TRACKERS           │
├──────────────────────────────────────┤
│   Jobs        Brand       Settings   │
└──────────────────────────────────────┘
```

- Account row: signed in → `PsAvatar` + email (or "Test account") + `PsPlanPill`; signed out →
  title "Sign in", supporting "Only needed to send links."
- Pro row: free → value = Play price when known, else "One payment"; Pro → title "Pairshot Pro",
  value "On".
- Connections value: free → `PsPlanPill`-styled "Pro"; Pro → nothing.

### 7.11 Account

```
┌──────────────────────────────────────┐
│ ←  Account                           │
│ (D)  dana@sparklewash.com     (Free) │
│ THIS MONTH                           │
│ ┌──────────────────────────────────┐ │
│ │ Client links            2 of 3   │ │  PsMeter
│ │ ━━━━━━━━━━━━━━━━━━──────         │ │
│ │ Photo storage      12 MB of 50 MB│ │
│ │ ━━────────────────────────       │ │
│ └──────────────────────────────────┘ │
│ Up to 30 photos a link. Links stay   │
│ live for 14 days, then their photos  │
│ are deleted.                         │
│ ┌──────────────────────────────────┐ │
│ │ Sign out                         │ │
│ └──────────────────────────────────┘ │
│ ┌──────────────────────────────────┐ │
│ │ Delete cloud data…               │ │  pencilText
│ └──────────────────────────────────┘ │
│ Your jobs and photos live on this    │
│ phone. The server only holds what you│
│ send to a client, until the link     │
│ expires.                             │
└──────────────────────────────────────┘
```

- Signed out: `titleM` "Send client links", `bodyS` "Sign in once to send links. Everything else in
  Pairshot works without an account.", the three sign-in facts, error line, `GoogleSignInButton`
  (or, when Google isn't configured, Secondary "Connect to the test server"). No usage block.
- Limit reached: `PsBanner(Warning, "This month's free links are used", "They come back on the 1st,
  or Pro removes the limit.", action "See Pro")` above the meters.
- "Test account" note (email null) stays as a `caption`.
- Confirms: Sign out and Delete cloud data use `PsConfirmDialog` with today's copy.
- Loading: `PsBusyBar`; meters show a `sunken` skeleton block until `usage` arrives. Offline/error:
  snackbar with the message (as today) and the meters block replaced by `caption` "Couldn't load
  your usage. Check your connection." with Quiet "Try again" (`vm.load()`).

### 7.12 Pairshot Pro

```
┌──────────────────────────────────────┐
│ ←                                    │
│ PAIRSHOT PRO                         │
│ Pay once.                            │  display serif
│ Keep it.                             │
│ No subscription. No ads on either    │
│ plan.                                │
│ ┌──────────────────────────────────┐ │
│ │                    FREE     PRO  │ │  mono column heads
│ │ Client links     3 a month  No limit
│ │ Photos a link       30      100  │ │
│ │ Links stay live   14 days   Until you close them
│ │ Edit after sending 3 days   Any time
│ │ "Made with Pairshot" On     Off  │ │
│ │ CRM connections      —      Yes  │ │
│ └──────────────────────────────────┘ │
├──────────────────────────────────────┤
│ [        Get Pro · $9.99           ] │  Accent
│          Restore purchase            │  Quiet
└──────────────────────────────────────┘
```

- Table: a `PsListBlock`; each row has the feature in `bodyS`, Free value in `bodyS` `inkSoft`, Pro
  value in `rowTitleS` `ink`. Each row is one merged node: "Client links: Free 3 a month, Pro no
  limit".
- Signed out: above the bottom bar a `caption` "Pro is tied to your Google account, so sign in
  first." and the bar's main button becomes `GoogleSignInButton`; after sign-in it becomes Get Pro.
- Price unknown (debug / Play unavailable): "Get Pro". Busy: `busy = true`.
- Already Pro: headline "Pro" inside `PsLoopMark` + "is on." ; body "Thank you. Everything below is
  yours for good."; the table stays with the Free column dimmed; bottom bar removed.
- Results arrive as snackbars from `vm.messages` (existing strings; change "Pro is on. Thank you!"
  to "Pro is on. Thank you." in `CloudViewModel.proMessage`: the only copy edit inside a ViewModel).

### 7.13 Connections (hub)

```
┌──────────────────────────────────────┐
│ ←  Connections                       │
│ Pairshot can take jobs from your CRM │
│ and tell it when a client opens a    │
│ link. Works with Jobber, Housecall   │
│ Pro, and anything Zapier or Make can │
│ reach.                               │
│ ┌──────────────────────────────────┐ │
│ │ ⇩ Jobs from your CRM             │ │
│ │   Last synced 12 min ago       › │ │
│ │ ⇧ Updates to your CRM            │ │
│ │   1 address                    › │ │
│ │ ⚿ API keys                       │ │
│ │   None yet                     › │ │
│ └──────────────────────────────────┘ │
│ ┌──────────────────────────────────┐ │
│ │ Step-by-step setup guide       ↗ │ │  opens {server}/help/crm
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

- Free plan: same intro, the three rows shown **disabled** (40 % alpha, no chevrons) so the user
  sees what Pro gives, and a `PsBottomBar` with Primary "See Pro". No pitch paragraphs here.
- Signed out: `PsEmptyState`-style block "Sign in first" + Secondary "Go to Account".

### 7.14 Jobs from your CRM

```
│ ←  Jobs from your CRM                │
│ Send jobs to this address from a     │
│ Zapier or Make step, or your CRM's   │
│ webhook. Each one appears in Jobs.   │
│ YOUR ADDRESS                         │
│ ┌──────────────────────────────────┐ │
│ │ https://pairshot…/in/9fK2…    ⧉ │ │  PsCodeWell
│ └──────────────────────────────────┘ │
│ [ Copy address ]                     │  Primary
│ ┌──────────────────────────────────┐ │
│ │ Sync now                         │ │  supporting: "Last synced 12 min ago. Syncs by itself every 30 minutes."
│ │ Make a new address…              │ │  supporting: "The old one stops working."
│ └──────────────────────────────────┘ │
```

No address (`inboundUrl == null`): `caption` "Sign in again to get your address." Confirm for new
address: today's copy.

### 7.15 Updates to your CRM (webhooks)

```
│ ←  Updates to your CRM               │
│ Pairshot tells these addresses when a│
│ link is sent, updated or first opened│
│ ┌──────────────────────────────────┐ │
│ │ hooks.zapier.com/hooks/catch/…   │ │  row per webhook: URL (2 lines max, mono)
│ │ Link sent, Link updated, Link…   │ │  supporting = event names
│ │                              ⋮   │ │  ⋮ = Send a test, Remove…
│ └──────────────────────────────────┘ │
│ [ + Add an address ]                 │  Secondary → PsSheet
│ ┌──────────────────────────────────┐ │
│ │ Recent deliveries     3 today  › │ │  hidden when there are none
│ └──────────────────────────────────┘ │
```

- Add sheet: `PsSheet("Add an address")`, `PsTextField(label = "Webhook address", placeholder =
  "https://hooks.zapier.com/…", helper = "From Zapier's "Catch Hook" step, Make, or your CRM.",
  error = "It has to start with https://")`, Primary "Add". On success the signing secret appears
  in `PsRevealDialog` ("Copy this now", today's copy).
- Empty: `PsEmptyState("No addresses yet", "Add one and Pairshot will tell it about your links.")`.
- **Delivery log** (`DeliveryLogRoute`): `PsTopBar("Recent deliveries")`, a `PsListBlock` of up to
  the returned deliveries: leading icon (check / clock / error, `ink`/`inkSoft`/`pencilText`),
  title "Link opened: Delivered" style (existing wording), supporting = relative time. Empty: "No
  deliveries yet."

### 7.16 API keys

```
│ ←  API keys                          │
│ Give a key to Zapier or Make so they │
│ can look up a job's link, list sent  │
│ links, or add jobs.                  │
│ ┌──────────────────────────────────┐ │
│ │ ps_live_3f9a…                    │ │  mono
│ │ Used 2 days ago              ⋮   │ │  ⋮ = Delete…
│ └──────────────────────────────────┘ │
│ [ Create a key ]                     │  Primary
```

Empty: "No keys yet." Reveal and delete confirm: today's copy through the shared dialogs.

### 7.17 Camera settings

```
│ ←  Camera                            │
│ WHEN YOU SHOOT AN AFTER, SHOW THE BEFORE AS
│ [ Outline | Photo | Off ]            │  PsSegmented
│ Red edges of the before. Easiest to  │  existing three descriptions
│ see in bright sun.                   │
│ ┌──────────────────────────────────┐ │
│ │   small static sample: a photo-  │ │  optional illustration: a sunken frame with the
│ │   shaped frame with the red      │ │  outline/ghost drawn at the chosen strength
│ └──────────────────────────────────┘ │
│ Strength                        60%  │
│ ─────────────●──────────────         │  PsSlider (hidden when Off)
│ You can change this while shooting   │
│ too. The camera remembers.           │
```

The sample frame is a simple Compose drawing (a few `pencil` strokes at `strength` alpha for
Outline; a grey block at `strength` alpha for Photo; empty for Off), marked `clearAndSetSemantics`.

### 7.18 Uploads & storage

```
│ ←  Uploads & storage                 │
│ ┌──────────────────────────────────┐ │
│ │ Upload on Wi-Fi only        ( ○) │ │  PsSwitchRow + existing supporting text
│ └──────────────────────────────────┘ │
│ ON THIS PHONE                        │
│ ┌──────────────────────────────────┐ │
│ │ Photos           1.2 GB (412)    │ │  PsValueRow
│ │ Free space            8.4 GB     │ │  value in pencilText when low
│ └──────────────────────────────────┘ │
│ FREE UP SPACE                        │
│ Delete jobs whose link was sent more │
│ than                                 │
│ (30 days)(90 days)(180 days)(1 year) │  PsChip radio
│ [ Delete 4 jobs · frees 310 MB… ]    │  Secondary; "No jobs that old" disabled
```

Confirm dialog: today's copy, destructive. Logic: `SettingsViewModel` storage functions unchanged.

### 7.19 Help & about

`PsListBlock`s: "Set up your CRM" (opens `{server}/help/crm`), "What Pairshot stores" (a `PsSheet`
with the existing privacy sentences: jobs and photos live on the phone; the server holds only what
you send, until the link expires; location and EXIF are removed before upload), "Licences" (sheet
listing Instrument Serif, Geist, Geist Mono under the SIL Open Font License, with the licence text
loaded from `assets/licences/*.txt`), `PsValueRow("Version", BuildConfig.VERSION_NAME)`.

### 7.20 Developer (debug builds only)

`PsTopBar("Developer")`, `caption` "Debug builds only.", `PsTextField("Server address")` with the
existing helper + Secondary "Save address"; `PsSwitchRow("Pretend this account is Pro", "Only works
on a local test server.")` when signed in; a row "Component gallery" opening the WP1 gallery screen.
The route and its Settings row are not registered when `BuildConfig.DEBUG` is false.

---

## 8. Templates for the final output

One idea ties the outputs together: a business picks a **look** once (page style + colour) and both
the client page and the share images follow it.

### 8a. Client page templates

Four templates, all rendered by the single file `shared/gallery/gallery.html`. The template is a
`data-template` attribute on `<html>`; each template is a block of CSS custom properties and a few
scoped rules, plus two small branches in the script (header markup; default compare mode). Shared
behaviour (slider, side-by-side toggle, full-screen viewer, crop, buttons, escaping, download link)
is identical across templates.

| Id | Name | Look | Suits |
|---|---|---|---|
| `classic` | **Classic** | Today's page, refined. Warm paper (`#F3F0EA` / dark `#141312`, follows the client's phone), Instrument Serif headline, mono eyebrows, slider cover, pill buttons. | Most trades; the default. Pressure washing, landscaping. |
| `showroom` | **Showroom** | Always dark (`#0E0D0C`, text `#F2EEE7`, muted `#A8A39A`, lines `#2A2826`). Cover runs edge to edge under a slim header; headline in the system sans, weight 600, tight; section labels in small letter-spaced caps; pairs one per row with 4 px corners; rectangular buttons (6 px). | Car detailing, anything glossy where dark makes the after pop. |
| `bright` | **Bright** | Always light (`#FFFFFF`, cards `#F5F7F8`, ink `#14171A`, muted `#5B6168`, lines `#E3E7EA`). A 6 px accent band across the top, then a round monogram (first letter of the business name on the accent) beside the name; sans headline weight 700; each pair sits in a soft bordered card (16 px corners) with its label under the photo; 12 px-corner buttons. | House cleaning, windows, carpets: clean, clinical, friendly. |
| `report` | **Report** | Always light, document-like (`#FFFFFF`, ink `#111111`, muted `#5F5F5F`, lines `#D9D9D9`). Header row: business name (serif) left, "Before & after report" in mono caps right, a 3 px accent rule, then a small facts table in mono labels (Service, Location, Date, Photos). Pairs default to **side by side** with a caption line (the label) under each; 2 px corners; outlined rectangular buttons. Has a print stylesheet (toggle and expand buttons hidden, pairs don't break across pages). | Gutters, commercial and property-manager work, insurance-style proof. |

Rules common to all:

- **Accent**: `--accent` is `business.accent` when present and valid; otherwise the template's
  fallback (`classic`: `#D4432A` light / `#E5553A` dark, exactly as today, so existing links do not
  change; `showroom`: `#F2EEE7`; `bright`: `#14171A`; `report`: `#111111`).
- `--on-accent` is computed in the script: white or `#111111`, whichever has the higher WCAG
  contrast against the accent. The accent is used only for **fills and rules ≥ 3 px** (primary
  button, top band, monogram, header rule, focus outline). It is never used as text colour. If the
  accent's contrast against the page background is below 3:1 (for example Ink on Showroom), fills
  get a 1.5 px border in the page's ink colour so the button edge is still visible.
- Fonts: system stacks only (no web-font request), as today.
- "Made with Pairshot" badge, review/book/download/call buttons, `noindex`, safe-URL and
  `textContent` handling: unchanged.
- Focus outlines, 44 px+ targets, `aria` labels and keyboard behaviour stay as they are in every
  template. Text contrast in each template's fixed palette is ≥ 4.5:1 (figures above were checked).
- Compare mode: `report` starts side by side, the others start on the slider. The viewer's own
  choice is remembered per template (`localStorage` key `pairshot-mode-<template>`).

**How the choice travels (the manifest)**

- New optional top-level field: **`"template"`**, one of `"classic" | "showroom" | "bright" |
  "report"`. `v` stays `1` (the change is additive; old phones and old stored manifests remain
  valid).
- `business.accent` already exists: `#RRGGBB`. The app now sets it.
- **Android** (`domain/gallery/GalleryManifest.kt`): `GalleryManifest` gains
  `val template: String? = null`; `BusinessInfo` gains `accent: String?`; `GalleryBuilder.build`
  gains a `template: ClientTemplate` parameter. The builder writes `template = null` when the
  resolved template is `classic`, and `accent = null` when the business has not picked a colour.
  Because the JSON omits nulls, a business that changes nothing produces byte-identical manifests
  to today, so **already-sent links do not show "You've changed things since sending"** after the
  update. Accent is normalised to upper-case `#RRGGBB`.
- **Server** (`server/src/manifest.ts`): `Manifest` gains `template?: "classic" | "showroom" |
  "bright" | "report"`. `parseManifest` keeps it only if it is one of the four ids (`str(o.template,
  20)` then an allow-list check); anything else is **dropped, never refused** (same spirit as
  `crop`). `accent` validation is unchanged. No migration: stored manifests without the field
  render as Classic.
- **Template** (`gallery.html`): `var T = ({classic:1,showroom:1,bright:1,report:1})[data.template]
  ? data.template : "classic"; document.documentElement.setAttribute("data-template", T);`
- Resolution on the phone: `job.template` (per-job override) → `settings.clientTemplate` (business
  default) → `classic`.

### 8b. Share-image (collage) templates

A share image = **layout** (chosen at share time) × **frame** (follows the business's page style and
colour automatically).

Layouts (`CollageLayout.Style`):

| Id | Name | Canvas | Description |
|---|---|---|---|
| `SIDE_BY_SIDE` | Side by side | long edge 2048, height from the photo | Existing. Before left, after right, footer. |
| `STACKED` | Before on top | long edge 2048 | Existing. |
| `SPLIT` | Split | one frame at the pair's aspect, long edge 2048 | The left half of the before meets the right half of the after along a white centre line with the round "‹ ›" knob, like the client page slider stopped at the middle. Shows off alignment, which is what Pairshot is for. |
| `SQUARE` | Square post | 2048 × 2048 | Header band with the business name, two cells side by side filling the rest (photos centre-cut to fit), footer. For Instagram and Facebook posts. |
| `STORY` | Story | 1152 × 2048 (9:16) | Header band, before over after, footer. For stories, reels covers, WhatsApp status. |

Frames (`CollageFrame`, picked from the business's page style; accent from the business colour, or
the frame's own ink when none):

| Page style | Background | Business name | Accent use | Tags |
|---|---|---|---|---|
| Classic | `#F3F0EA` | Instrument Serif, `#1B1A18` | 6 px rule above the footer | Mono caps, white on 55 % black |
| Showroom | `#0E0D0C` | Geist 600, letter-spaced caps, `#F2EEE7` | 6 px rule above the footer | Same |
| Bright | `#FFFFFF` | Geist 600, on-accent colour | Footer/header band filled with the accent | Same |
| Report | `#FFFFFF` | Geist Mono caps `#111111`: "BUSINESS · LABEL" | 3 px rule | Same |

- Tags read BEFORE / AFTER. Free plan keeps "MADE WITH PAIRSHOT" in the footer (right-aligned,
  mono, muted); Pro has none. With no business name and Pro, header/footer bands collapse to zero.
- Output: JPEG quality 90, as today, in `cacheDir/exports`.
- `CollageLayout.plan` stays pure maths and unit-tested; `Plan` gains `headerHeight` (default 0) and
  the three new styles. `CollageExporter` is split into `render(...) : Bitmap` (takes a `longEdge`
  so the same code draws thumbnails) and `export(...) : File`.

**The share sheet** (`pair/ShareImageSheet.kt`, a `PsSheet("Share as one image")`):

```
│ Share as one image                   │
│ ┌──────────────────────────────────┐ │
│ │        large live preview        │ │  the real render of the selected layout, ~720 px
│ └──────────────────────────────────┘ │
│ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐        │  five real mini renders (240 px) of THIS pair,
│ └───┘ └───┘ └───┘ └───┘ └───┘        │  PsSelectableFrame, horizontal scroll
│ Side   Stack Split Square Story      │
│ Free adds a small "Made with Pairshot" mark.  See Pro
│ [             Share                ] │  Primary
```

- Opens on the layout used last time (`SettingsStore.collageLayout`, default `SIDE_BY_SIDE`).
- Thumbnails render off the main thread; until ready each shows a `sunken` block at the right
  aspect. Selecting is instant (the large preview cross-fades, `standard`).
- "Share" exports at full size (button `busy`, "Making image…"), then opens the system share sheet.
  Failure: snackbar "Couldn't make the image. Try again."
- TalkBack: thumbnails are a radio group: "Split, selected".

### 8c. The picker

- **Where**: (1) onboarding step 2 (section 9); (2) the Brand tab's Look section, any time; (3)
  per job, Preview → Style (page style only; colour is the business's and is not per job).
- **Colour**: nine curated swatches plus "your own". All nine give ≥ 5:1 with white text, so
  buttons are always readable.

  | Name | Hex |  | Name | Hex |
  |---|---|---|---|---|
  | Ink | `#1C1B19` | | Plum | `#6A2C5B` |
  | Navy | `#1E3A5F` | | Rust | `#A8431C` |
  | Blue | `#1F5FBF` | | Red | `#B8321F` |
  | Teal | `#0F6F68` | | Slate | `#475361` |
  | Green | `#2F6B3A` | | | |

  Shown in this order: Ink, Slate, Navy, Blue, Teal, Green, Plum, Rust, Red.
  "Your own" (`+` swatch) opens a `PsSheet("Your colour")` with a `PsTextField` for a hex code
  (`#` prefilled, 6 hex digits, live preview swatch, error "Six letters or numbers, like 1F5FBF.").
  Any colour is accepted; the page handles contrast (8a).
- **Page style**: the four `PsTemplateThumb`s in a row. A thumb is a Compose drawing, not a
  WebView: the page background of that template, the business name in that template's header style,
  a split before/after block (the cover pair of the most recent job with a finished pair if there is
  one, via `PhotoImage`; otherwise two neutral tones `#8B8478` / `#CBD3C6`), a short line for the
  title, and the primary button in the accent. It must be recognisably different per template at
  104 × 148 dp. Marked as one node for TalkBack with the template's name and blurb.
- **Defaults that look right with zero setup** (applied at onboarding from the chosen trade, and
  editable there):

  | Trade | Colour | Page style |
  |---|---|---|
  | Pressure washing | Navy | Classic |
  | Window cleaning | Blue | Bright |
  | Gutter cleaning | Slate | Report |
  | Car detailing | Ink | Showroom |
  | Carpet cleaning | Plum | Bright |
  | House cleaning | Teal | Bright |
  | Landscaping | Green | Classic |
  | Something else | Ink | Classic |

  People who onboarded before this update keep `accent = null`, `template = classic` (their pages
  look exactly as before) until they choose something in Brand. Brand shows no swatch selected and
  the hero uses the Classic fallback red in that case.
- Blurbs (shown under the row for the selected style): Classic "Warm paper and a serif. Suits most
  work."; Showroom "Dark and glossy. Makes a finish shine."; Bright "White and crisp. Clean work,
  cleanly shown."; Report "Side by side, like a record of the job."

---

## 9. Onboarding

Two steps, under a minute, no account, nothing uploaded. A two-dot progress mark at the top; system
back on step 2 returns to step 1.

**Step 1 — who you are**

```
┌──────────────────────────────────────┐
│ ● ○                                  │
│                                      │
│  (Pairshot)                          │  display serif inside PsLoopMark (static loop)
│                                      │
│ Before and after photos, sorted by   │
│ job, sent to your client as one      │
│ clean link.                          │
│                                      │
│ Business name                        │
│ [ Sparkle Wash                     ] │
│ WHAT DO YOU DO?                      │
│ (Pressure washing)(Window cleaning)  │
│ (Gutter cleaning)(Car detailing)…    │
│ This only sets the label names we    │
│ suggest.                             │
├──────────────────────────────────────┤
│ [             Next                 ] │  Primary; always enabled (name optional, as today)
└──────────────────────────────────────┘
```

**Step 2 — what your client sees**

```
┌──────────────────────────────────────┐
│ ● ●                           Skip   │  Skip keeps the trade's defaults and finishes
│ This is what your client gets.       │  titleL serif
│ ┌──────────────────────────────────┐ │
│ │        PsTemplateThumb           │ │  hero, 200×284 dp, carries the business name typed in
│ │   (live: name, colour, style)    │ │  step 1 ("Your business" if blank)
│ └──────────────────────────────────┘ │
│ ● ● ● ● ● ● ● ● ●                    │  PsSwatch row, the trade's colour preselected
│ ( Classic )( Showroom )( Bright )( Report )│ PsChip radio, the trade's style preselected
│ Warm paper and a serif. Suits most.  │
│ You can change this any time in Brand.│
├──────────────────────────────────────┤
│ [             Start                ] │  Primary
│ No account needed. Everything stays  │
│ on this phone until you send a link. │
└──────────────────────────────────────┘
```

- Changing the trade on step 1 re-applies that trade's defaults on step 2 unless the user has
  already touched the colour or style.
- The hero changes with a `standard` cross-fade. No loop animation here (the animated loop belongs
  to Sent).
- "Start" calls `finishOnboarding(name, trade, accent, template)` and lands on Jobs, where the
  empty state's button says "New job".
- Accessibility: each step's heading receives focus on entry; dots are described "Step 1 of 2".

---

## 10. Data and logic changes the spec requires

Everything else in `data/`, `domain/`, `work/`, `di/` and the ViewModels stays as it is. These are
the only permitted changes outside `ui/`:

1. `data/prefs/Settings.kt`
   - `enum class ThemeMode { SYSTEM, LIGHT, DARK }`; `AppSettings.themeMode` (key `theme_mode`,
     default SYSTEM); `setThemeMode`.
   - `AppSettings.accent: String?` (key `accent`, default null); `setAccent(String?)`.
   - `AppSettings.clientTemplate: ClientTemplate` (key `client_template`, default CLASSIC);
     `setClientTemplate`.
   - `AppSettings.collageLayout: String` (key `collage_layout`, default `SIDE_BY_SIDE`);
     `setCollageLayout`.
   - `finishOnboarding(name, trade, accent, template)`.
2. New `domain/gallery/ClientTemplate.kt`: `enum class ClientTemplate(val id, val label, val blurb)`
   with `fromId(String?)`, plus `object BrandLook { val colours: List<BrandColour>; fun
   defaultsFor(trade): Pair<String, ClientTemplate> }` (tables of 8c). Pure Kotlin, unit-tested.
3. `domain/gallery/GalleryManifest.kt`: `template` field, `BusinessInfo.accent`, `build(template =
   …)` as in 8a. `GallerySender.build/fingerprint` and `PreviewViewModel` pass
   `ClientTemplate.fromId(job.template) ?: settings.clientTemplate` and `settings.accent`.
4. `data/db/Entities.kt` + `AppDatabase.kt`: `JobEntity.template: String? = null`; database version
   5; `MIGRATION_4_5` = `ALTER TABLE jobs ADD COLUMN template TEXT`; add to `ALL_MIGRATIONS`;
   extend `MigrationTest`. `JobRepository.setTemplate(jobId, template: String?)`. `CrmSync` already
   merges with `existing.copy(...)`, so the column survives CRM updates; add one assertion to
   `CrmSyncTest` to pin that.
5. `domain/CollageLayout.kt`, `ui/components/CollageExporter.kt`: as in 8b.
6. `server/src/manifest.ts`: as in 8a.
7. `shared/gallery/gallery.html`: as in 8a.
8. `ui/settings/SettingsViewModel`: also expose `account: StateFlow<CloudState?>` from
   `CloudSession.state` (read-only) for the Settings tab's first two rows.
9. New `ui/brand/BrandViewModel` (wraps `SettingsStore` setters for name, trade, phone, review,
   booking, message template, accent, client template; plus the latest finished cover pair for the
   thumbnails via `JobRepository`). The same setters are removed from the Settings screen's use but
   may stay on `SettingsViewModel`.
10. `CloudViewModel.proMessage`: "Pro is on. Thank you." (drop the exclamation mark).
11. `MainActivity`: resolve `themeMode` → `PairshotTheme(dark)`, set system-bar icon appearance.

No new Gradle dependency. One new font file and three licence text assets.

---

## 11. Work packages

Seven packages, built one after another, each leaving the app compiling, running and all tests
green. Common rules for every package:

- **Build discipline (7.4 GB RAM under WSL): one Gradle task at a time, never two Gradle processes,
  never Gradle and the server tests at once.**
  ```
  cd /home/jewel/projects/b4after/android
  JAVA_HOME=~/.local/toolchain/jdk17 ./gradlew :app:compileDebugKotlin
  JAVA_HOME=~/.local/toolchain/jdk17 ./gradlew :app:testDebugUnitTest
  JAVA_HOME=~/.local/toolchain/jdk17 ./gradlew :app:lintDebug
  ```
  Server (only WP5): `cd /home/jewel/projects/b4after/server && PATH=~/.local/toolchain/node/bin:$PATH npx vitest run`.
  Client page (only WP5): `cd /home/jewel/projects/b4after/shared/gallery && PATH=~/.local/toolchain/node/bin:$PATH npm test`.
- **Never touch**: `brand/GreasePencil.kt`, `brand/GreasePencilStudio.kt`, `docs/grease-pencil/**`
  (except adding `fonts/Geist-OFL.txt` in WP1), `data/cloud/**`, `data/billing/**`, `work/**`,
  `di/**`, camera capture logic, anything in `server/` except what WP5 lists. No ViewModel logic
  changes beyond section 10. Do not commit; do not add dependencies.
- **Common acceptance criteria** (apply to every package in addition to its own):
  1. The three Gradle tasks above pass, run one at a time.
  2. Every screen touched has been looked at in **light and dark** (previews or device) and at font
     scale 1.3; no clipped text, no unreadable pair.
  3. Every interactive element has a 48 dp target and a TalkBack label; headings are marked;
     toggles announce state; nothing relies on colour alone.
  4. No stock M3 `Card`, `TopAppBar`, `FilterChip`, `AssistChip`, `NavigationBar`, FAB or raw
     `Color(0x…)` literal in screen code touched by the package (tokens only; `PsCameraColors` and
     `domain`/exporter palettes excepted).
  5. No logic regression: every action that existed before still exists and does the same thing;
     existing unit tests are untouched except where the package says so.
  6. Copy follows the voice guide; no exclamation marks.
  7. A short hand-off note lists files changed, anything that deviated from the spec and why.

### WP1 — Tokens, theme, component library

Goal: the design system exists and is reviewable on its own; the app still looks roughly as before
but on the new colours and fonts.

Create
- `res/font/geist.ttf`; `docs/grease-pencil/fonts/Geist-OFL.txt`;
  `app/src/main/assets/licences/{InstrumentSerif-OFL,Geist-OFL,GeistMono-OFL}.txt`.
- `ui/theme/PsColors.kt`, `PsType.kt`, `PsTokens.kt` (space, shape, motion), `PsCameraColors.kt`.
- `ui/components/ps/`: `PsScaffold.kt`, `PsBars.kt` (TopBar, TabHeader, TabBar, BottomBar,
  PencilStroke), `PsButtons.kt`, `PsRows.kt` (SectionHeader, ListBlock, ListRow, SwitchRow,
  ValueRow), `PsInputs.kt` (TextField, SearchField, Chip, Segmented, Slider, Swatch),
  `PsStatus.kt` (StatusPill, PlanPill, Tag, Banner, Meter, BusyBar), `PsSurfaces.kt` (Sheet,
  ConfirmDialog, RevealDialog, SnackbarHost, CodeWell, SelectableFrame), `PsMedia.kt` (PhotoThumb,
  PairTile, Avatar, PairArt, EmptyState, LoopMark), `PsMotion.kt` (psPressable, reduced motion).
- `ui/dev/ComponentGalleryScreen.kt`: every component in every state on one scrolling screen, with
  a light/dark toggle at the top. Reachable in debug builds (temporary entry: long-press the "Jobs"
  title; WP4 moves it to Developer).

Modify
- `ui/theme/Theme.kt` (tokens → Material mapping, `PairshotTheme(dark)`), `data/prefs/Settings.kt`
  (`ThemeMode` only), `MainActivity.kt` (theme mode + system bars), `res/values/colors.xml`,
  `res/values-night/*`.

Do not touch: any screen file beyond what is needed to compile.

Acceptance: common criteria; the gallery screen shows all components of section 6 in both themes;
contrast table of 4.1 implemented exactly; Geist renders at weights 400/500/600; `PsTemplateThumb`
is **not** in this package (WP5).

### WP2 — Shell, navigation, Jobs, New job, Brand (basics)

Goal: three tabs work; Jobs and New job are final; Brand holds the business fields.

Create: `ui/nav/Tabs.kt` (tab model), `ui/brand/BrandScreen.kt`, `ui/brand/BrandViewModel.kt`,
`ui/brand/MessageScreen.kt`, `ui/home/JobRow.kt`.
Modify: `ui/nav/AppNav.kt`, `ui/nav/Routes.kt` (add `BrandRoute`, `MessageRoute`; keep `CloudRoute`
working until WP4), `ui/home/HomeScreen.kt`, `ui/job/NewJobScreen.kt`,
`ui/settings/SettingsScreen.kt` (remove the business, client-page-button and message fields, which
now live in Brand; the rest stays until WP4).
In this package Brand contains: header, Business block, On the page block, Message row. The hero
and Look section arrive in WP5 (leave no placeholder text; simply omit them).

Do not touch: `HomeViewModel`, `NewJobViewModel`, `domain/HomeOrder.kt`, `statusOf`/`progressText`
signatures.

Acceptance: common criteria; tab state is kept when switching; back behaviour per 7.0; the
notification deep link still opens the job; search, sections, low-space banner and empty state per
7.1; message editor per 7.9 with unit test for token insertion if any pure helper is added; business
fields save as typed with no cursor jump.

### WP3 — The job flow

Goal: Job, Link panel, Sent moment, Sign-in sheet, Pair, sheets, Crop editor, Camera chrome.

Create: `ui/job/LinkPanel.kt` (keeps `LinkState`, `looksStalled`, `STALL_MILLIS`, `LinkActions`;
`LinkCard.kt` is deleted only after its public names are preserved in the same package),
`ui/job/JobBottomBar.kt`, `ui/pair/PairTools.kt`, `ui/pair/MoreSheet.kt`.
Modify: `ui/job/JobScreen.kt`, `ui/job/SentMoment.kt`, `ui/cloud/SignIn.kt`,
`ui/pair/PairScreen.kt`, `ui/pair/CropEditor.kt`, `ui/pair/RePairSheet.kt`,
`ui/components/LabelSheet.kt`, `ui/components/BeforeAfterSlider.kt` (knob, tags, radius only),
`ui/camera/CameraScreen.kt` (chrome only), `ui/preview/PreviewScreen.kt` (top bar and frame only;
Style arrives in WP5).

Do not touch: `JobViewModel`, `PairViewModel`, `CameraViewModel`, `GhostLoader`, `VolumeKeys`, crop
maths (`dragCrop`, `centredCrop`), `StallTest`. The share action keeps the old two-item choice in a
`PsSheet` list until WP6.

Acceptance: common criteria; the bottom-bar state table of 7.3 holds for every `SendState` ×
`remoteState` × `changed` combination (builder lists the combinations checked); every link state
string from the old `LinkCard` is still reachable; the Sent moment still plays once per first send
and is skippable; volume-key shutter, peek, zoom restore, undo still work on a device or emulator;
every Pair action that existed is reachable in ≤ 2 taps from the Pair screen.

### WP4 — Settings, Account, Pro, Connections

Goal: the Settings tab and every page under it; `CloudScreen.kt` is gone.

Create: `ui/settings/SettingsScreen.kt` (rewritten as the tab root), `CameraSettingsScreen.kt`,
`StorageScreen.kt`, `AboutScreen.kt`; `ui/account/AccountScreen.kt`, `ProScreen.kt`,
`ConnectionsScreen.kt`, `IncomingJobsScreen.kt`, `CrmUpdatesScreen.kt`, `DeliveryLogScreen.kt`,
`ApiKeysScreen.kt`, `DeveloperScreen.kt`, `AccountNav.kt` (the nested graph and shared-ViewModel
helper).
Modify: `ui/nav/AppNav.kt`, `ui/nav/Routes.kt` (remove `CloudRoute`), `ui/cloud/CloudScreen.kt`
(reduced to `CloudViewModel`, `CloudExtras` and the pure helpers; file may be renamed
`CloudViewModel.kt`), `ui/settings/SettingsViewModel` (expose `account`),
`CloudViewModel.proMessage` (copy only).

Do not touch: `CloudViewModel` behaviour, `CloudSession`, `ProBilling`, `CrmSync`, `GoogleAuth`.

Acceptance: common criteria; feature table 5.3 verified row by row (builder ticks each); one
`CloudViewModel` instance across the account graph (no reload when moving between its pages);
snackbars, reveal-once dialogs and all confirms appear on the page that triggered them; Developer
row and route absent from release builds (`BuildConfig.DEBUG` false); free, Pro, signed-out and
offline states of 7.11–7.16 each checked in both themes; the promise line and studio lockup end the
Settings tab.

### WP5 — Client page templates and the look pickers

Goal: four templates end to end: settings, manifest, server, page, pickers, per-job override.

Create: `domain/gallery/ClientTemplate.kt`, `ui/components/ps/PsTemplateThumb.kt`,
`ui/brand/LookSection.kt`, `ui/preview/StyleSheet.kt`; tests `ClientTemplateTest`,
additions to `GalleryBuilderTest`, `GallerySenderTest` (fingerprint unchanged for classic/no
accent), `MigrationTest` (4→5), `CrmSyncTest` (template survives).
Modify: `data/prefs/Settings.kt` (accent, clientTemplate), `data/db/Entities.kt`,
`data/db/AppDatabase.kt`, `data/db/Daos.kt` and `data/repo/JobRepository.kt` (`setTemplate`),
`domain/gallery/GalleryManifest.kt`, `data/cloud/GallerySender.kt` (**only** the `build` companion
call site, to pass accent and template: this is the single allowed edit under `data/cloud/`),
`ui/preview/PreviewScreen.kt`, `ui/brand/BrandScreen.kt` + `BrandViewModel.kt`,
`shared/gallery/gallery.html`, `shared/gallery/test/gallery.test.mjs`, `server/src/manifest.ts`,
`server/test/galleries.test.ts`.

Do not touch: other server files; the slider/viewer/crop script logic in `gallery.html` beyond what
the templates need; `public.ts`.

Acceptance: common criteria, plus:
- A manifest built for Classic with no accent is byte-identical to one built by the current code
  for the same job (test).
- Server: valid ids kept; unknown, wrong-typed or over-long `template` dropped without a 400; old
  manifests still parse (tests). All existing server tests pass.
- Client page tests: each of the four templates renders hero, sections, buttons and badge without
  errors; unknown template falls back to Classic; `--on-accent` is dark on a light accent and white
  on a dark one; Report starts side by side; text from the business still cannot inject HTML; the
  existing eight tests pass unchanged.
- The four pages checked by eye at 360 px and 1024 px wide, light and dark system setting, with a
  long business name, with no buttons, with one pair and with 30 pairs. Keyboard: slider, toggle,
  viewer still operable.
- Brand hero and thumbs update live; per-job override changes only that job; "Update link" appears
  on a live link after a style change.

### WP6 — Share-image templates

Goal: five layouts, four frames, the share sheet with live thumbnails.

Create: `ui/pair/ShareImageSheet.kt`, `domain/CollageFrame.kt` (pure palette/metrics per page
style), tests added to `CollageLayoutTest` (new styles: boxes inside the canvas, no overlap, exact
canvas sizes for SQUARE and STORY, header/footer collapse).
Modify: `domain/CollageLayout.kt`, `ui/components/CollageExporter.kt`, `ui/pair/PairScreen.kt`
(open the sheet), `data/prefs/Settings.kt` (`collageLayout`).

Do not touch: `Crop`, `Bitmaps`, `PhotoPrep`, `CenterCropTest` expectations.

Acceptance: common criteria; each layout × each frame exported once and inspected (20 images):
no text clipped with a 40-character business name, tags legible, crop respected, watermark present
on free and absent on Pro; thumbnails render off the main thread and the sheet opens without a
visible stall; peak bitmap memory stays within one full-size canvas plus one decoded photo at a
time (recycle as the current code does).

### WP7 — Onboarding and the finishing pass

Goal: the two-step first run, then a whole-app sweep.

Create/modify: `ui/onboarding/OnboardingScreen.kt` (+ `OnboardingViewModel.finish` with accent and
template), `data/prefs/Settings.kt` (`finishOnboarding` signature), `README.md` (status list:
tabs, templates), `PLAN.md` §3 "First open" line and the Settings bullet in §4 (wording only).
Sweep: transitions per 4.6 in `AppNav`, reduced-motion check, empty/loading/error states of every
screen against section 7, TalkBack run through the main flow (first run → new job → shoot → pair →
preview → send → Brand → Settings → Account), font scale 1.3 and 2.0, both themes, a small phone
(360 × 640 dp) and a large one. Remove the temporary gallery entry from Jobs. Delete dead code and
unused imports left by earlier packages.

Acceptance: common criteria; a first-time user reaches Jobs in two taps plus optional typing;
trade defaults of 8c applied and overridable; existing users are not shown onboarding again and
their pages are unchanged; the builder's note includes a table of every screen × {light, dark} ×
{checked}.

---

## 12. Director's review checklist

Run per package, then once more over the whole app.

Direction
- [ ] At arm's length the screen reads as paper, ink and photos. Red appears only as: the Send /
      Update / Get Pro fill, Sent/Seen marks, the tab stroke, error/destructive text, the camera
      ghost, the loop.
- [ ] At most one accent-filled button on any screen; one obvious primary action per screen.
- [ ] No shadows, gradients, tinted Material containers, or cards inside cards.
- [ ] The loop animates only in the Sent moment. Static marks only where section 6 allows.

Consistency
- [ ] Only `Ps*` components and tokens in screen code; no stray radii, paddings, colours or font
      sizes. Gutters are 20 dp; rows 56/72 dp; buttons 52 dp.
- [ ] Serif only for titles ≥ 22 sp; mono only for eyebrows and facts; sentence case everywhere.
- [ ] Top bars, sheets, dialogs and snackbars are the shared ones on every screen.
- [ ] It sits comfortably next to Swipe and Album: same paper, same type roles, same press feel,
      same Settings ending.

Information architecture
- [ ] Every row of table 5.3 is where it says, at the stated depth.
- [ ] Settings root has no text fields. Nothing developer-flavoured is visible above depth 2.
- [ ] The words webhook, signature, secret, API appear only on their own pages.
- [ ] Developer is absent from release builds.

States
- [ ] Each screen's empty, loading, error, offline, free, Pro and signed-out states match section 7.
- [ ] Job bottom bar matches the state table for every link state.

Both themes and accessibility
- [ ] Light and dark screenshots of every screen; no pair below AA; no pure white or pure black
      surfaces outside the camera and photo viewers.
- [ ] Forcing Light/Dark in Appearance changes the app and the system-bar icons immediately.
- [ ] TalkBack: every control named, headings navigable, toggles and radios announce state, status
      changes announced, traversal order matches visual order.
- [ ] All targets ≥ 48 dp. Font scale 1.3 clean, 2.0 usable. Animator scale 0 respected.

Templates
- [ ] Four client page styles are distinct at a glance, each credible for a paying business, each
      correct with any swatch and with a hostile custom colour (pale yellow, pure white, pure black).
- [ ] Existing sent links are unchanged and not flagged as changed after the update.
- [ ] Thumbnails carry the user's name and colour and match what the real page looks like.
- [ ] Five share layouts × four frames look intentional; the alignment-flattering Split is right to
      the pixel at the seam.
- [ ] Zero-setup defaults per trade look good without touching anything.

Copy
- [ ] Voice guide followed; no exclamation marks, no jargon on main paths, no sentence used as a
      button label; existing copy preserved where the spec did not change it.

Engineering hygiene
- [ ] Unit tests, lint, server tests, client page tests green; tasks were run one at a time.
- [ ] No new dependencies; no edits to the brand kit, cloud, billing or worker code beyond
      section 10; no ViewModel behaviour change beyond section 10.
- [ ] Hand-off note present, deviations explained.
