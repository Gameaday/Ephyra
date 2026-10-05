# Phase 1 — Navigation Transitions: Audit & Actions

Date: session branch `cline/0crpxa7t`.

## Already in place (verified)
- **Root NavHost** (`MainActivity`): shared-axis-X transitions driven by a real motion
  policy (`MotionPolicy` / `motionPlanFor`) with route-pair semantics, pop-aware
  direction, asymmetric durations (longer forward, 200ms back), and a shared-cover
  element between library/history/updates and series details.
- **Tab NavHost** (`HomeScreen`): order-aware slide for peer tabs, fade-through for
  non-tab destinations, reduced-motion respected on all four transition slots.
- **Reduced motion**: `rememberSystemReducedMotion` + `LocalMotionPreference` provided
  once at the root, read by both NavHosts.
- **Predictive back**: manifest `enableOnBackInvokedCallback="true"`; sheet-level
  `PredictiveBackDraggableProgress` in presentation-core.
- **Structural tests**: `MotionConsistencyTest`, `LibrarySeriesTransitionTest`,
  `PredictiveBackTest`, `TabNavControllerLifetimeTest` guard the rules that are
  invisible in review.

## Fixed this session
- Root NavHost **fallback transitions** (undeclared route pairs / no cover id) used
  `m3SharedAxisX*` unconditionally, ignoring reduced motion while the plan path
  respected it — the setting was inconsistent per route. All four fallback slots now
  return `EnterTransition.None`/`ExitTransition.None` under reduced motion.
  Commit: `fix(nav): honour reduced motion in root NavHost fallback transitions`.

## Remaining candidates (not done)
- `motionPlanFor` returns null when neither entry has a cover id, so non-cover pairs
  (e.g. READER_ENTRY from a shortcut) always take the generic fallback instead of
  their declared `ContainerMotion.SHARED_AXIS` plan. Decide: intended simplification
  or policy gap?
- Shared-cover transitions exist for library/history/updates → details, but not for
  browse-source results or global search → details. Extending `mangaCoverKey` usage
  there would unify "cover carries the transition" app-wide.
- Reader is a separate Activity with window anims; verify distances still match
  `MotionTokens` after any token change (structural test covers the drift risk).
- No feature module defines its own NavHost (good — single place to govern motion).

## Deferred to device validation
- 60fps check on S24 with macrobenchmark; flash-of-blank on back press cannot be
  verified without a device/emulator run (no JDK in this sandbox for local tests).

## Motion rework (second pass, owner-reported issues)

Six defects found and fixed against current Material 3 guidance:

1. **Tab slides travelled the full viewport with no fade** (M2 carousel anti-pattern:
   both screens fully swapped at midpoint, double overdraw, incoming page arrives from
   off-screen which is a hierarchy cue, not a peer cue). Now 30% axis travel + fade.
2. **Reduced-motion check ran after the fade-through branch** in HomeScreen, so nested
   destinations animated (fade + scale) even with animations disabled. Reordered.
3. **Fade-through carried a scale-in** — M2 leftover; a full-page scale reads as a zoom
   (modal cue) and forces full-screen re-rasterisation every frame. Now fade-only.
4. **`containerEnter(SHARED_AXIS)` used shared-axis-Z (zoom)** for hierarchical
   navigation — the anti-pattern Motion.kt's own docs describe. Now shared-axis-X.
5. **Shared-axis-X exit leg was 450ms**, as long as the enter leg; M3 outgoing legs are
   short (200ms). Fixed in the token and in `shared_axis_x_push_exit.xml` (Activity half).
6. **Dead policy pairs**: `TAB_PEER`, `READER_ENTRY`, `SHEET_PARENT`, `UNDECLARED` had
   no production call site — rules asserted by tests but executed by nothing. Removed;
   `MotionRoutePair` now declares only pairs a caller can actually name, with a note
   documenting where each removed pair's motion genuinely lives.

Commits: `bb0b4a8`, `d4b9492`, `3f1c6ed`.

## Third pass (final)
7. **Coil crossfade ran inside the shared element** — the cover flashed placeholder-to-image
   underneath its own flight. Disabled for shared-element participants (`bf96fd0`).
   Caveat: call sites that pass a pre-built `ImageRequest` bypass this; Phase 4's shared
   request builder should make crossfade-off-for-shared-elements the default there too.
- Verified: ReaderActivity open/close window anims match token travel/durations; shared
   element key single-sourced via `MotionPolicy.mangaCoverKey`; aspect-ratio ordering in
   the modifier chain already fixed upstream.

## Still open
- `m3SharedAxisZ*` token functions are now unreferenced; retained as vocabulary for a
  future modal/sheet use, but delete if nothing claims them within a release or two.
- Device validation (60fps, overdraw on S24) still pending hardware.
