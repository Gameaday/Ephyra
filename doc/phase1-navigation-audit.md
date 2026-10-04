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
