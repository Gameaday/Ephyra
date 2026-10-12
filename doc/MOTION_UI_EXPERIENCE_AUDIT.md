# Motion, Material UI & Back-Experience Audit — Findings and Plan

> **Status:** forward-looking audit + decision document. Branch `cline/ejmzhbkf`.
> **Trigger:** owner report that "the material UI design and the material expression
> transitions and back experience are not enjoyable", with a request to either improve
> the whole experience, remove it until it can be done better, or make it toggleable.
> **Scope:** the app-wide Material 3 surface (theme/design tokens), navigation motion
> (root NavHost, tab NavHost, Activity window anims, reader), and every back surface.

This document is an audit, not a record of completed work. Nothing here has been
implemented yet. Each finding cites the code it was read from so it can be re-verified.

---

## 1. Verdict up front

The motion system is **not** the problem people usually mean by "unfinished": it is
unusually well specified. There is a written contract (`doc/MOTION_NAVIGATION_CONTRACT.md`),
a single decision layer (`MotionPolicy`), asymmetric Material-3-correct durations, a
shared-element cover flight, predictive-back plumbing, and structural tests that guard
rules no reviewer could see.

The report is still real, and it decomposes into **four distinct causes**, only one of
which is "the animation is bad":

1. **A genuine back-navigation bug** that makes back from a non-Library tab leave the
   app (F1). This is the single highest-leverage fix and it is not a taste issue.
2. **No in-app escape hatch.** Motion can only be reduced by changing the *OS* animator
   scale (F2). A user who finds the app's motion unpleasant has no recourse inside it.
3. **A design-token and legibility risk** in the branded themes: translucent surfaces
   are applied globally to the M3 color scheme without any contrast check (F5).
4. **Under-governance.** The rules are enforced by tests that can pass on dead code, and
   most back surfaces do not participate in the predictive-back gesture (F1, F8).

**Recommendation:** do **not** remove the work wholesale. Ship an escape hatch plus the
real bug fix first (Phase 0), then harden and finish the experience behind gates
(Phases 1–3). Full removal (Option C) is only justified for individual components that
fail the Phase-1 acceptance gates. See §5–§6.

---

## 2. What already exists (verified inventory)

| Layer | Where | State |
|---|---|---|
| Motion tokens (durations, easings, springs, presets) | `presentation-core/.../theme/Motion.kt` | Complete; M3 Expressive vocabulary |
| Motion policy (which route pair moves how) | `core/domain/.../navigation/motion/MotionPolicy.kt` | Correct but thin — one pair |
| Root NavHost transitions | `app/.../ui/main/MainActivity.kt` (4 slots, `SharedTransitionLayout`) | Wired; reduced-motion honored |
| Tab NavHost transitions | `app/.../ui/home/HomeScreen.kt` (order-aware 30% slide + fade) | Wired |
| Shared-cover flight (library/history/updates/source/search → series) | `MainActivity.hostsSharedCover`, `MangaCover` | Wired; Coil crossfade off for shared element |
| Predictive back | manifest opt-in; `presentation-core/.../ui/navigation/PredictiveBack.kt` | Wired for **sheets** and the **series page** only |
| Reduced motion | `presentation-core/.../ui/navigation/LocalMotionPreference.kt` | **OS animator scale only** |
| Reader motion (separate Activity) | `ReaderActivity` window anims + `ViewerConfig` | Wired; own toggles |
| Themes | `presentation-core/.../theme/EphyraTheme.kt` — 17 `AppTheme`s, 3 branded configs | Wired |
| Structural gates | `MotionConsistencyTest`, `LibrarySeriesTransitionTest`, `PredictiveBackGateTest`, `TabNavControllerLifetimeTest` | Present (see F1, F8 for gaps) |

Reader already exposes two user toggles: **Animate page transitions**
(`ReaderPreferences.pageTransitions()`) and **Always show chapter transition**
(`ReaderPreferences.alwaysShowChapterTransition()`). The *app* exposes none.

---

## 3. Findings

Severity is about user-visible impact and certainty, not effort.

### F1 — Back from a non-Library tab exits the app (HIGH, confirmed)

`HomeScreen` installs a `BackHandler` intended to keep back *inside* a tab:

```kotlin
// app/src/main/java/ephyra/app/ui/home/HomeScreen.kt:164-169
val backStackEntry by bottomNavController.currentBackStackEntryAsState()
val currentRoute = backStackEntry?.destination?.route
if (currentRoute != null && currentRoute !in TAB_ROOT_ROUTES) {
    BackHandler {
        bottomNavController.popBackStack(ScreenRoutes.Library.route, inclusive = false)
    }
}
```

`bottomNavController` **only ever holds the five tab-root routes**. It is navigated to
exclusively with `tab.route` (`HomeScreen.kt:181`, `:335`, `:375`) and its `NavHost`
registers only those five (`HomeScreen.kt:261-300`). Nested destinations live on the
*root* controller (`LibraryScreen(navController = externalNavController)` etc.). So
`currentRoute !in TAB_ROOT_ROUTES` is **always false** and the handler never registers.

Consequence: pressing back while on Updates, History, Browse, or More falls through to
the root `NavController` (parked on `ScreenRoutes.Home`) and **exits the app** — it does
not return to the Library tab or to the previous tab.

Two further problems in the same block:

- Even if it fired, it pops to `ScreenRoutes.Library.route` unconditionally. The comment
  above it (`HomeScreen.kt:154-163`) says "back inside a tab returns to that tab's own
  root", which is not what the code does.
- The gate `TabNavControllerLifetimeTest."back inside a tab is intercepted rather than
  leaving the app"` (`app/src/test/.../TabNavControllerLifetimeTest.kt:88-102`) asserts
  only that the source *contains* `BackHandler` and matches
  `BackHandler\s*\{[^}]*popBackStack\(`. It passes on dead code, so the intended
  behaviour was never actually verified. This is an inert gate, the same failure class
  the repo's own history documents repeatedly.

### F2 — Motion can only be reduced by changing the OS setting (HIGH for the report)

`LocalMotionPreference` / `rememberSystemReducedMotion` read
`Settings.Global.ANIMATOR_DURATION_SCALE == 0` (`LocalMotionPreference.kt:26-34`) and
nothing else. There is no app preference. `UiPreferences` exposes theme, date format,
tablet mode, and description images only — no motion setting.

So the user's option "toggle it" does not exist today at app level. A user who dislikes
the app's motion but keeps system animations on (which is the default) cannot turn it off
without turning off *all* system animations, including for other apps.

### F3 — Two definitions of one reader transition toggle (MEDIUM, confirmed)

- Live: `ReaderPreferences.alwaysShowChapterTransition()` → key
  `"always_show_chapter_transition"` (`ReaderPreferences.kt:56`), consumed by
  `ViewerConfig.kt:74`, `PagerViewer`, `WebtoonViewer`.
- Dead: `ReaderPreferences.chapterTransitionEnabled()` → key
  `"pref_enable_chapter_transitions"` (`ReaderPreferences.kt:202`), read by nothing
  (repo-wide grep: definition only).

Two keys, two names, one behaviour. The dead one is a trap for the next change.

### F4 — Motion policy covers one route pair; unused Z-axis vocabulary (MEDIUM)

`MotionRoutePair` deliberately declares a single entry, `LIBRARY_SERIES`
(`MotionPolicy.kt:11-22`), because tab motion, reader entry, sheets, and undeclared
routes are owned elsewhere. That is defensible, but it means the "policy" enforces one
rule. Separately, `MotionTokens.m3SharedAxisZ*` have **zero call sites** outside
`Motion.kt` (verified by grep). The prior audit already flagged these as "delete if

### F5 — Branded themes lower surface alpha globally with no contrast check (HIGH risk)

`EphyraTheme.applyBrandedAlpha` rewrites the color scheme's surfaces to translucent:

```kotlin
// presentation-core/.../theme/EphyraTheme.kt:118-131
surface = surface.copy(alpha = config.surfaceAlpha)            // Ephyra: 0.85
surfaceContainer* = ...copy(alpha = config.containerAlpha)     // Ephyra: 0.78
```

`onSurface` / `onSurfaceVariant` are left untouched, so the *declared* contrast is
computed against an opaque surface while the *rendered* surface is a blend of the
translucent color over the window background. For the Ephyra ("glassmorphic") theme this
is a measurable legibility risk for body text on `surfaceContainer*`, and it is a strong
candidate for "the material UI design is not enjoyable" — the surfaces read as washed
out rather than layered. No test measures rendered contrast, and the three branded
configs (`BrandedThemeConfig.kt`) are applied to every M3 component through
`MaterialTheme`, so the effect is app-wide, not opt-in per component.

### F6 — Dynamic-color (Monet) scheme can go stale (MEDIUM)

`BaseEphyraTheme` builds the scheme under `remember(appTheme, isDark, isAmoled)`
(`EphyraTheme.kt:79-88`) without keying on `context`. `MonetColorScheme(context)`
therefore can retain colors from a previous configuration/context. Recorded in
`doc/historical/UI_AUDIT.md` and still open.

### F7 — Design-token leaks and no gate for them (MEDIUM)

Still open from `doc/historical/UI_AUDIT.md`:

- Hardcoded banner colors + `Color.White` text, bypassing `MaterialTheme`
  (`presentation-core/.../components/AppStateBanners.kt:19-21, 44+`).
- Inline `tween(...)` literals outside `MotionTokens`
  (`SettingsSearchScreen.kt:222,227`; `ReaderScreen.kt:126`; `CalendarHeader.kt:77-84`).
- Duplicate `WindowSize` helper (`app/.../designsystem/utils/` vs
  `presentation-core/.../util/`); the app copy is unreferenced and can drift.
- Hardcoded cover placeholder color bypassing the token system.

The audit's own recommendation — a structural gate for "no raw `tween(n)` and no raw
`Color(...)` outside the token/theme layer" — was never added.

### F8 — Back surfaces do not participate in predictive back (MEDIUM)

The manifest opts into `enableOnBackInvokedCallback="true"`. There are ~12 `BackHandler`
call sites (selection modes in Library/Updates/Manga/Migration, search-in-tab in
Sources/Extensions, WebView history, onboarding), but only the series page uses the
shared `PredictiveBackProgress` helper. An intercepting surface that does not participate
gets **no gesture preview** — the user swipes, nothing moves, and it vanishes at the end.
`PredictiveBackGateTest` only scans `presentation-core/src/main`, so feature-module
handlers are not gated and this gap is invisible to CI.

### F9 — Transient feedback is split across two channels (LOW)

`Effect.ShowToast` and snackbar effects coexist, producing mixed/duplicated feedback
(`UI_AUDIT.md` P-M3). Not motion per se, but part of "the app feels unpolished".

### F10 — No device-level motion validation (LOW / process)

60 fps and overdraw for the transitions have never been measured; the prior audit
deferred this to "device validation pending hardware". `PERFORMANCE_BUDGETS.md` exists
but no motion-specific budget is wired.

---

## 4. Root causes (why this keeps happening)

1. **Enforcement is structural and can pass on dead code.** `TabNavControllerLifetimeTest`
   asserts the *presence* of a handler, not that it can fire. Every motion rule here is
   a fact about code that does not run; the tests read source, so a rule can be "guarded"
   while the behaviour is absent. (F1, F8)
2. **Escape hatches were treated as the OS's job.** Reduced motion delegates entirely to
   the platform, so the app has no per-app control. (F2)
3. **Design tokens are applied globally but validated locally (or not at all).** Branded
   alpha and typography are pushed through `MaterialTheme`; nothing measures the result. (F5)
4. **The system is real but thin.** One policy pair, one dead pref, unused tokens, and
   token leaks that no gate catches. (F3, F4, F7)

nothing claims them within a release or two".


---

## 5. The three options

### Option A — Improve completely

Finish and harden the whole experience: fix F1, add an in-app motion preference, fix
contrast (F5) and Monet staleness (F6), close the token leaks (F7), make every
intercepting back surface predictive (F8), and add gates that cannot pass on dead code.

- **Pros:** keeps the tested work; addresses the report at its root; the motion system is
  already closer to Material 3 than most apps.
- **Cons:** largest effort; the "not enjoyable" complaint may be about taste as much as
  correctness, so gates must include a human/device check, not only structural tests.

### Option B — Toggle it (escape hatch first)

Ship a user-facing **Motion & transitions** preference immediately: `Full / Reduced / Off`
app-wide, plus the two existing reader toggles surfaced alongside it. Fix F1 (the real
bug) and delete F3 (dead pref) at the same time.

- **Pros:** smallest change that directly answers the report; gives users relief now;
  low regression risk; the preference is the precondition for A being safe to iterate on.
- **Cons:** does not by itself make the design *better*; adds a preference surface that
  must be honoured everywhere or it becomes another inconsistency.

### Option C — Remove until it can be done better

Strip expressive transitions (fall back to platform defaults), remove branded alpha, and
delete the unused tokens.

- **Pros:** strongest guarantee of a plain, predictable experience.
- **Cons:** discards work that is already correct and guarded (asymmetric shared-cover
  flight, predictive back helper, token vocabulary); a "plain" app still needs *some*
  motion or navigation reads as a cut; removal is not reversible cheaply. Not recommended
  wholesale.

---

## 6. Recommended plan

**Hybrid: B now, then A behind gates; use C only per-component if a gate fails.**
Rationale: F1 and F2 are the concrete, reproducible complaints, and both are cheap. The
rest is real but should be validated before being rewritten, because the design direction
is partly taste.

### Phase 0 — Stop the bleeding (Option B, ~small)

1. **Fix F1.** Decide the intended tab-back model and implement it:
   - *Preferred:* back returns to the **previous tab** (a real tab history), falling
     through to the system only at the Library (start) tab. This matches the code's own
     comment and the standard Android pattern.
   - Alternative: back returns to the **Library tab** from any other tab, then exits.
   Either way, replace the dead `currentRoute !in TAB_ROOT_ROUTES` predicate with one that
   can actually fire, and fix the test so it fails when the handler cannot run.
2. **Add the app-level motion preference (F2).** `MotionPreference { FULL, REDUCED, OFF }`
   in `UiPreferences`; provide it through `LocalMotionPreference` merged with the existing
   system value (effective reduced = `systemReduced || pref != FULL`; `OFF` additionally
   collapses element-level animations, not just navigation). Expose it in Appearance.
3. **Delete the dead pref (F3)** and point any migration at the live key.
4. **Gate that cannot pass on dead code:** assert the tab back model behaviourally
   (Robolectric/Compose test driving `bottomNavController`), not by string presence.

**Exit gate:** a user can turn the app's motion down or off without touching OS settings;
back from every tab does something deliberate and documented; the new test fails if F1 is
reintroduced.

### Phase 1 — Legibility and design tokens (Option A, design half)

5. **Fix F5.** Measure rendered contrast for the branded themes (WCAG AA for body text)
   and either raise `surfaceAlpha`/`containerAlpha` floors, scope translucency to
   non-text surfaces, or derive `onSurface` against the composited color. Add a contrast
   test so a future alpha change fails loudly.
6. **Fix F6** (key the scheme on `context`).
7. **Fix F7** (banner colors → theme roles; inline tweens → tokens; dedupe `WindowSize`)
   and add the structural gate the prior audit proposed ("no raw `tween(`/`Color(` outside
   the token/theme layer").

**Exit gate:** branded themes pass an automated contrast check; no raw tween/color outside
the token layer; Monet survives a configuration change.

### Phase 2 — Back experience everywhere (Option A, interaction half)

8. **Fix F8.** Route every intercepting back surface through the shared predictive helper
   (or explicitly document why a surface cannot preview), and widen
   `PredictiveBackGateTest` to scan feature modules.
9. Audit the remaining `BackHandler` sites for correctness of *what* they pop to, the way
   F1 was audited — presence is not behaviour.

**Exit gate:** every surface that intercepts back either previews the gesture or is
recorded as intentionally non-previewable; a structural test covers feature modules.

### Phase 3 — Consolidate motion (Option A, motion half)

10. **F4:** delete `m3SharedAxisZ*` if no modal/sheet use claims them, or wire them to the
    one place they belong (modal entry); widen `MotionRoutePair` only when a call site
    names a new pair (the existing rule).
11. **F10:** add a motion performance budget and capture a macrobenchmark on a real device.
12. **F9:** unify transient feedback on one channel.

**Exit gate:** no unreferenced motion vocabulary; motion has a measured performance
budget; the contract in `doc/MOTION_NAVIGATION_CONTRACT.md` matches the code.

### What would justify Option C for a component

If a specific transition fails Phase 1/2 gates and cannot be made both legible and
predictable without a rewrite, remove **that component's** motion (fall back to the
platform default) rather than the whole system, and record why in the contract.

---

## 7. Open decisions for the owner

1. **Tab back model** — previous-tab history, or always-to-Library? (Drives Phase 0.1.)
2. **Motion preference shape** — three-way `Full/Reduced/Off`, or a simple
   "reduce motion" boolean merged with the OS setting?
3. **Design direction** — is the Ephyra glassmorphic look itself the complaint (→ redesign
   surfaces), or only its legibility (→ fix alpha/contrast and keep the look)?
4. **Appetite** — Phase 0 only, or commit to Phases 1–3?

