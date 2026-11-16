# Pairshot UI revamp — director's review

Reviewed: commits d2791cd, 717a57f, ae953b5, 817f913, 1615c3b, 9ee5cf4, 502061a against `SPEC.md`
(section 12 checklist and each package's acceptance criteria).

How it was judged: from the code, plus the 20 Robolectric collage renders (`CollageRenderTest`
output, 5 layouts x 4 frames, 40-character business name). No device or emulator was available and
this shell has no JDK, so **I did not run unit tests, lint, server tests or the client page tests**;
nothing below is a claim about them. Items marked *verify on device* are read from code only.

Paths are relative to `android/app/src/main/java/com/greasepencil/pairshot/` unless they start with
`shared/`.

## Overall

The direction landed. Tokens match section 4.1 exactly, the Material mapping is right, screens use
only `Ps*` components (no stray colour literals outside the camera, crop and photo overlays, no
`MaterialTheme.*` in screen code, no ad-hoc dialogs), red is confined to the allowed uses, Developer
is debug-only, the account graph shares one `CloudViewModel`, and reduced motion is honoured in the
shell, tab stroke, press feel and Sent moment. What is left is one real rendering bug in the share
image, one layout bug in the shared list row, and a set of accessibility and theme edges.

## 1. Verdict per package

| Package | Verdict | Why |
|---|---|---|
| WP1 Tokens, theme, components | **Pass with fixes** | M2, M8, M9, M11 |
| WP2 Shell, Jobs, New job, Brand basics | **Pass with fixes** | M2 shows on Brand; S1 |
| WP3 Job flow | **Pass with fixes** | M3, M5, M6 |
| WP4 Settings, Account, Pro, Connections | **Pass with fixes** | M9, M10 |
| WP5 Client page templates, pickers | **Pass with fixes** | M4, M7 |
| WP6 Share-image templates | **Pass with fixes, one blocker** | M1 fails the "no text clipped with a 40-character business name" criterion |
| WP7 Onboarding, finishing pass | **Pass with fixes** | M11; S4 |

## 2. Must fix (11), most severe first

### M1. Share image: the business name overdraws the watermark and runs off the canvas (Stacked)
`ui/components/CollageExporter.kt:94-109`

Seen in the renders. `classic-stacked.png`: "Sparkle & Shine Exterior Clea" is drawn straight over
"MADE WITH PAIRSHOT" and is cut by the right edge, with no ellipsis. `bright-stacked.png`: the
business name is cut to "SPARKLE & SHI…" while the Pairshot mark sits beside it at full size. The
business's own name loses to our mark, on the image they post publicly.

Likely cause of the overdraw: the width is measured with `p.textSize = 1f` (line 104), where glyph
advances are rounded, so the serif measures too narrow and `fitSize` returns a size that does not
fit. The truncation is a layout choice: the mark is laid out first and the name gets what is left.

Fix:
1. Measure at a large reference size: `p.textSize = 100f; val widthAtOne = p.measureText(name) / 100f`,
   and set `p.isSubpixelText = true` and `p.isLinearText = true` on the name paint.
2. Give the name priority in a shared footer. Lay out the name first with
   `maxWidth = plan.width - pad * 2` less a mark capped at 38 % of the band width; shrink the mark's
   text to fit that cap (floor `footerHeight * 0.16f`). If the name still does not fit at
   `preferred * 0.55f` beside the mark, drop the mark's size to the floor before ellipsising the name.
3. After choosing the size, always re-measure and ellipsise if `p.measureText(text) > maxWidth`
   (never trust the first measurement).
4. Add a test beside `CollageRenderTest` that, for every style x frame with the 40-character name,
   asserts name right edge <= mark left edge and <= `plan.width - pad`. Re-export the 20 images.

### M2. `PsListRow`: a long `value` squeezes the title to nothing
`ui/components/ps/PsRows.kt:215-218` (visible at `ui/brand/BrandScreen.kt:59`)

The value `Text` has no weight, no width limit and no line limit, so the row measures it first and
gives the weighted title what is left. Brand's "Name" row with a 40-character business name leaves
the word "Name" a few dp wide, wrapping letter by letter. `PsValueRow` (`PsRows.kt:285-287`) has the
same shape.

Fix, in both rows: `Text(value, …, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier =
Modifier.widthIn(max = 180.dp))`. Add a gallery/preview case with a 40-character value.

### M3. Camera in the light theme: status-bar icons are dark on a near-black screen (*verify on device*)
`ui/camera/CameraScreen.kt:131` and `:280`; `ui/pair/CropEditor.kt:82`

`MainActivity` sets the bar icon colour from the app theme. The camera and crop editor are always
dark, so in the light theme the clock and battery are dark on `#0E0E0D`.

Fix: at the top of `CameraScreen`:
```kotlin
val view = LocalView.current
DisposableEffect(Unit) {
    val bars = WindowCompat.getInsetsController((view.context as Activity).window, view)
    val status = bars.isAppearanceLightStatusBars
    val nav = bars.isAppearanceLightNavigationBars
    bars.isAppearanceLightStatusBars = false
    bars.isAppearanceLightNavigationBars = false
    onDispose { bars.isAppearanceLightStatusBars = status; bars.isAppearanceLightNavigationBars = nav }
}
```
In `CropEditor` do the same on the dialog's window:
`(LocalView.current.parent as? DialogWindowProvider)?.window`.

### M4. Client page: focus outline and accent rules vanish with a hostile colour
`shared/gallery/gallery.html:47`, `:69`, `:88`, `:110`, `:126`

`data-edge` (accent under 3:1 against the page) only edges the primary button. The focus outlines
are still `2px solid var(--accent)`, so with white or pale yellow on Bright/Report (or Ink on
Showroom) keyboard focus is invisible on the slider, mode toggle and viewer buttons. Report's 3 px
header rule and Bright's top band disappear the same way.

Fix, after line 88:
```css
html[data-edge] .ba:focus-within,
html[data-edge] .expand:focus-visible,
html[data-edge] .mode button:focus-visible,
html[data-edge] .viewer button:focus-visible { outline-color: var(--ink); }
html[data-edge][data-template="report"] header.top { border-bottom-color: var(--ink); }
html[data-edge][data-template="bright"] body { border-top-color: var(--ink); }
```
Add a `gallery.test.mjs` case: accent `#FFFFFF` on Bright sets `data-edge`.

### M5. Link panel: Text / Share / Copy break mid-word at font scale 1.3 on a 360 dp phone
`ui/job/LinkPanel.kt:265-279`

With a client phone there are three buttons plus the 48 dp overflow in 296 dp. Each button gets
about 75 dp, 51 dp of it for text; "Share" at 15 sp x 1.3 needs about 54 dp, so it wraps as
"Shar / e". Spec 4.2 requires 1.3 to be clean.

Fix: move the overflow (`Box { PsIconButton(MoreVert, "Link options") … }`) out of the button row
to the end of the "CLIENT LINK" header row, after the status pill (drop the pill's `end = 8.dp`
there). The three buttons then share 272 dp (about 66 dp of text each). When
`LocalDensity.current.fontScale > 1.5f`, stack the three buttons in a `Column`, each `fullWidth`.

### M6. Cover tool is announced as a switch that cannot be switched off
`ui/pair/PairTools.kt:62-70`

`psToggleable(cover, { onCover() }, Role.Switch)`: when the pair is already the cover TalkBack says
"Cover, switch, on", and double-tapping does nothing. Hidden/Shown is a true toggle; Cover is a
choice of one pair in the job.

Fix: `Modifier.weight(1f).psSelectable(selected = cover, onClick = onCover, role = Role.RadioButton,
focusShape = Ps.shape.md)`, keep the `stateDescription`. No ViewModel change.

### M7. Report page says the job title twice in a row
`shared/gallery/gallery.html:361-365`

The `h1` is `job.title` and the first facts row is "SERVICE {job.title}". Two identical lines at the
top of a page meant to look like a record.

Fix: remove `["Service", job.title]` from the facts array (Location, Date, Photos stay). Update the
Report test to match.

### M8. Keyboard focus ring is invisible on the camera and crop editor in the light theme
`ui/components/ps/PsMotion.kt:52`

The ring is always `Ps.colors.ink`, which is near-black in the light theme, on always-dark chrome.

Fix: wrap the content of `CameraScreen` and of `CropEditor`'s dialog in `PairshotTheme(dark = true) { … }`
so every `Ps.colors` read inside is the dark set (the `onDark` parameters keep working and the label
sheet over the camera goes dark with it, which is correct for an always-dark screen).

### M9. Pro page has an empty heading in its top bar
`ui/components/ps/PsBars.kt:78-85`, called from `ui/account/AccountNav.kt:109`, `ui/account/ProScreen.kt:95`

`PsTopBar("")` still emits a `heading()` node with no text, which TalkBack lands on, while the real
title ("PAIRSHOT PRO") is not a heading.

Fix: in `PsTopBar`, emit the title `Text` only `if (title.isNotEmpty())` (keep the weighted `Column`
as the spacer). In `ProScreen.kt:95` add `.semantics { heading() }` to the eyebrow and remove it from
the two display lines at `:97` and `:108`, so the page has one heading.

### M10. Settings tells a free user "Plan: Pro" on the Connections row
`ui/settings/SettingsScreen.kt:86`

`PsPlanPill(pro = true)` is reused as a "needs Pro" marker; its spoken text is "Plan: Pro", so the
row reads "Connections, Plan: Pro" to someone on Free.

Fix: give `PsPlanPill` a `spoken: String? = null` parameter (`PsStatus.kt:66`) and pass
`spoken = "Needs Pro"` here.

### M11. Colour swatches: the selected ring is clipped by its neighbours and the two pickers differ
`ui/components/ps/PsInputs.kt:462-470`, `ui/brand/LookSection.kt:107-110`, `ui/onboarding/OnboardingScreen.kt:282`

A 40 dp circle + 3 dp gap + 2 dp ring is 50 dp, drawn in a 48 dp box. The row wraps to two lines on
a 360 dp phone with no vertical gap, so the ring touches the swatch above or below. Brand spaces
swatches by 4 dp; onboarding by 0.

Fix: in `PsSwatch` draw the circle at radius 18 dp (36 dp circle; ring ends at 23 dp, inside the
box). Add `PsSwatchRow(modifier, content)` to `PsInputs.kt`: a `FlowRow` with `selectableGroup()`,
`horizontalArrangement = spacedBy(4.dp)`, `verticalArrangement = spacedBy(4.dp)`, and use it in both
places. Update the spec's "40 dp" to 36 dp.

## 3. Should fix

- **S1. Tab bar leaving makes the pushed screen jump.** `ui/nav/AppNav.kt:55-75`. The bar fades for
  160 ms holding its height, then the column snaps 64 dp taller mid-slide (the Job bottom bar drops).
  This is my spec's layout, so not a builder defect. Better: put `AppGraph` and the bar in one `Box`,
  bar aligned to the bottom over the graph, and give the three tab roots bottom padding equal to the
  measured bar height through a `CompositionLocal`.
- **S2. Dialog window.** `ui/components/ps/PsSurfaces.kt:129-158`. Inside `Dialog`, set
  `(LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(Ps.colors.scrim.alpha)`;
  add `.border(1.dp, c.hairline, Ps.shape.lg)` to `DialogCard` (in dark the surface barely separates
  from the dimmed page); make the button row a `FlowRow` aligned to the end so "I've saved it" /
  "Copy & close" wrap at font scale 2.0. *Verify on device*: with Appearance forced against the
  system setting, status-bar icons while a `PsSheet` is open (Material 3 1.3.2 picks them from the
  system theme); if wrong, set them from `Ps.colors.isDark` on the sheet's window the same way.
- **S3. Spacing tokens.** About 230 raw `N.dp` literals in screen code. All are on the 4-point
  scale, so it looks right, but the checklist asks for tokens: replace with `Ps.space.*`. Gaps
  between blocks on Account and Pro are 16/24 where 4.3 says 32 (`AccountScreen.kt:107,120,131`,
  `ProScreen.kt:117`). Also `PsRows.kt:123` hard-codes 14 dp instead of reading the `md` radius, and
  `SentMoment.kt:83` moves by `24f` pixels, not dp.
- **S4. Onboarding.** `OnboardingScreen.kt:165-169`: steps swap with no transition; use
  `AnimatedContent` with the `standard` duration (skipped under reduced motion). `:276`: on a
  360 x 640 dp phone the 200 x 284 hero pushes both pickers below the fold; use 160 dp wide when the
  window is under 700 dp tall.
- **S5. Share sheet height.** `ui/pair/ShareImageSheet.kt:96-98`: the fixed 320 dp preview puts the
  Share button below the fold on small phones. Use `min(320.dp, 40 % of screen height)`.
- **S6. Collage tag scale.** Tags are about 45 % larger on Side by side than on Square at the same
  width, and on Report they are larger than the business name. Derive the tag size from the canvas
  long edge, not the cell.
- **S7. "Appearance" is marked as a heading** (`SettingsScreen.kt:97`); it is a label. Remove
  `heading()`.
- **S8. Add-address error shows on the first keystroke** (`CrmUpdatesScreen.kt:131`). Only flag it
  when the text is not a prefix of `https://` and is at least 8 characters, or on Add.
- **S9. Small things.** `PsAvatar` letter overflows its 44 dp circle at font scale 2.0 (cap with a
  fixed dp-derived size); `PsMenuItem` shows Material's ripple where everything else uses the press
  scale; `PairTools.kt:59` uses a 12 dp side inset where the page gutter is 20.
- **S10. Hand-off note.** The checklist asks for one; the deviations live only in commit bodies. Add
  `docs/ui-revamp/HANDOFF.md` with the deviation list and the screen x theme table WP7 required.

## 4. Rulings on reported deviations

| Deviation | Ruling |
|---|---|
| Dialogs use Android's default dim | **Accept for now, fix cheaply** (S2). Only the light theme differs (about 60 % vs 40 %). |
| Switch unchecked thumb and segmented border changed for 3:1 | **Accept.** The spec's values failed its own contrast rule; I will amend section 6. |
| Settings Pro row says "One payment" | **Accept.** Section 7.10 allows it when the price is unknown, and loading billing on the Settings root is not worth it. |
| Report shows the job title twice | **Reject.** Fix as M7. |
| Report's primary button filled with the accent | **Accept.** The common rule (accent fills the primary button) wins over "outlined buttons", which stands for the secondary ones. Spec to be clarified. |
| Cover tool can't be turned off | **Accept the behaviour** (a job always has a cover; no ViewModel change), **reject the switch semantics.** Fix as M6. |
| "Pick the after from your gallery" in the More sheet | **Accept.** It is in section 7.5's More list; not a deviation. |
| `PsMenu` / `PsMenuItem`, `psSheetBleed`, `PsButton` compact | **Accept.** All three belong in the library; add them to section 6. Note S9 on the menu ripple and M5 on compact. |
| Onboarding step 2 has only the nine swatches | **Accept.** That is what section 9 draws; "your own" stays in Brand. |
| Accent falls back to frame ink when it would vanish | **Accept.** Right call for the collage rule. The client page needs the same care for focus and rules (M4). |

## 5. Fixed

Builder's follow-up. Unit tests (203, 0 failures), lint (0 errors), `assembleDebug`, server
`tsc` + vitest (104 passed) and the client page tests (22 passed) all pass.

### Must fix

| Item | What was done |
|---|---|
| M1 | `CollageExporter` words now go through `wordsOf()`: name measured at 100 px with subpixel and linear text; mark capped at 38 % of the width (floor `footerHeight * 0.16`); if the name won't fit at its minimum beside the mark, the mark drops to its floor first; the chosen size is re-measured (ink bounds as well as advance) and stepped down, and the name is ellipsised only if it still doesn't fit. New `CollageRenderTest` case checks every style x frame with the 40-character name: name right edge <= mark left edge and <= `width - pad`, and an ellipsised name only with the mark at its floor. All 20 renders re-exported and checked by eye: no overlap, nothing cut by the edge. |
| M2 | `PsListRow` and `PsValueRow` values: `maxLines = 1`, ellipsis, `widthIn(max = 180.dp)`. Preview cases with a 40-character value added. |
| M3 | New `AlwaysDark(window)` in `Theme.kt` sets light bar icons while it's shown and restores them after. Used by `CameraScreen` (activity window) and `CropEditor` (its dialog window). *Still to verify on device.* |
| M4 | Added the `data-edge` rules for the slider, expand and mode-toggle focus rings, the Report header rule and the Bright top band. One change from the ruling: viewer buttons always get a white focus ring, because the viewer is always near-black and an ink ring would vanish there on Bright/Report. Tests added for the edge rules (pale yellow on Report) next to the existing white-on-Bright case. |
| M5 | The overflow menu moved to the CLIENT LINK header row, after the pill (whose `end = 8.dp` was dropped). Text / Share / Copy share the row (with 8 dp end padding), and stack full-width in a `Column` when `fontScale > 1.5`. |
| M6 | The Cover tool now uses `psSelectable(selected = cover, role = Role.RadioButton)`, keeping its `stateDescription`. |
| M7 | Removed "Service" from the Report facts; test updated, and a new test checks the title isn't repeated. |
| M8 | Done by the same `AlwaysDark` wrapper: it provides `PairshotTheme(dark = true)` around the camera and crop editor content. |
| M9 | `PsTopBar` draws no title `Text` when the title is empty. On Pro, the "PAIRSHOT PRO" eyebrow is the only heading. |
| M10 | Added `PsPlanPill(spoken = …)`. The Connections row passes `"Needs Pro"`. |
| M11 | `PsSwatch` circle is now 18 dp radius (36 dp). New `PsSwatchRow` (FlowRow, `selectableGroup`, 4 dp gaps both ways) is used in Brand and onboarding. SPEC's component table updated to 36 dp. |

### Should fix

Done:
- **S2**, except the part to check on a device: dialogs use `Ps.colors.scrim.alpha` as their dim amount, the card has a 1 dp hairline border, and the buttons sit in an end-aligned `FlowRow`.
- **S3**, in part: the gaps on Account and Pro now use `Ps.space.sectionGap` (32), `PsListBlock` reads its radius from `Ps.shape.md`, and the Sent moment moves by `24.dp`.
- **S4**: onboarding steps use `AnimatedContent` with a fade at `standard` duration (0 under reduced motion), and the hero is 160 dp wide when the window is under 700 dp tall.
- **S5**: the share preview is `min(320.dp, 40 % of the window height)`.
- **S7**: removed `heading()` from "Appearance".
- **S8**: the address error shows only once the address is 8 or more characters and doesn't start with `https://`, or after tapping Add. Add is enabled whenever the field isn't empty, and tapping it with a bad address shows the error.
- **S9**, in part: `PsAvatar`'s letter has a fixed 20 dp size, and the Pair tools use the 20 dp gutter.

Skipped:
- **S1**: it changes the spec's shell layout and needs a device to judge the motion.
- **S2**, the sheet status-bar check: it needs a device.
- **S3**, replacing about 230 raw dp literals with tokens: too large and mechanical for this pass. Every value is already on the 4-point scale.
- **S6**: tag scale changes the share-image layout and needs a judgment by eye across all layouts.
- **S9**, the `PsMenuItem` ripple: Material's `DropdownMenuItem` would need replacing with a custom item, which isn't a small change.
- **S10**: the hand-off note needs the deviation list collected from the commit bodies. Left for its own task.
