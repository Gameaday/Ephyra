# Motion and Navigation Contract

> **Status:** binding technical contract. Motion is assigned by semantic route pair, not globally.

## Assignment matrix

| Route pair | Cover | Non-cover content | Back |
|---|---|---|---|
| Library ↔ Series | Shared cover only | Held still (no fade) | Same model, shorter timeline |
| Any cover list ↔ Series | Shared cover only | Held still (no fade) | Same model, shorter timeline |
| Tab peer | None | Directional horizontal slide | Tab history model, same slide reversed |
| Hierarchical destination | Optional shared element | Shared axis X | Reverse shared axis X |
| Activity destination (reader, WebView) | None | Pure-alpha window crossfade (theme fades, 200ms) | Same crossfade; gesture commits through finish() with a drag dim |
| Compact pane → expanded pane | Optional item identity | Directional pane | Directional reverse |
| Sheet/dialog → parent | None | Component motion | Parent state |

## Tab peers slide along the bar, they do not fade

The five bottom-nav destinations are ordered, not merely adjacent, so a tab change is a move along
one axis rather than a swap of unrelated screens. A fade-through is the right answer for peers with
no order; here it made each tab read as a discrete screen that happened to replace the previous one,
and made going back a second dissolve instead of a reversal.

The direction is derived from each tab's index in the bar, so the incoming page always enters from
the side the tapped button sits on and returning to an earlier tab is the same movement played the
other way. Enter and exit share one duration with complementary easing, so the two pages travel
together and settle at the same instant — two halves resolving at different points is what reads as
discrete rather than cohesive.

A destination that is not a tab root (a nested screen reached inside a tab) keeps the fade-through:
it has no position on the tab axis, and sliding it would imply an ordering that does not exist. The
direction helper is therefore tri-state — forward, backward, or *not a tab pair* — so "not a tab
pair" can never be silently folded into "backward".

Under reduced motion the slide collapses to an instant state change, like every other transition.

## A series is reachable from every list that shows its cover

The shared-cover pair is not "Library to Series". It is **any destination that both renders
`MangaCover` with the manga's id and provides an animated-visibility scope**, to Series and back.
`MainActivity.hostsSharedCover` names that set.

Two obligations make a destination a host, and a destination that satisfies only one of them is a
defect rather than a partial success:

- it must render the cover with the id it will open, or the shared element has no counterpart;
- it must provide `LocalNavAnimatedVisibilityScope`, or the element cannot participate at all.

When only one end has the element the transition does **not** degrade gracefully: the pair's
container motion is a deliberate no-op, so with nothing left to animate the whole change becomes a
cut. That is why the pair was widened from the tab shell to source results and global search, and why
each host's scope is asserted.

`MangaDetails` is deliberately excluded even though it satisfies both obligations, because the
direction rule reads "leaving a series page" as backward and a forward move to a *related* series
would then take the shorter return timeline.

## An Activity destination's motion is one app-owned crossfade

The reader (and the WebView) are separate Activities, so they cannot use the Compose transition.
Their boundary motion has changed hands twice, and both reversals are recorded here because each
one answered the defect before it.

**Override era.** The windows mirrored the shared axis: `overrideActivityTransition` registered
the push on open and the pop on close, with the four `shared_axis_x_*.xml` files asserted against
the Compose token's travel and durations. Once the app opted into predictive back
(`enableOnBackInvokedCallback`), this broke: on Android 14+ the back **gesture** runs the system's
cross-activity animation and bypasses a custom close transition, so the custom pair only played on
the paths that did not go through the gesture. One boundary, three motions (gesture, affordance,
entry) — the owner-reported defect of 2026-11-08: the back motion returning from pages to the main
tab "is not consistent and looks bad".

**Platform era.** The fix was to stop overriding: the activities registered no window transition,
and the boundary was deliberately left to the platform. That made every exit path move the same
way — consistent — but the platform's answer was the Android 14+ predictive reveal, which scales
and slides the window: it reads as elements flying and zooming into the background, and the open
path played no preview at all. The owner's motion audit of 2026-11-09 rejected it and locked the
directive: **a minimal crossfade with no movement at this boundary, owned by the app on every
path.**

**Current rule.** The boundary is one app-owned crossfade:

- **the theme renders the window fades.** `Theme.Tachiyomi.ActivityBoundary` (declared on the
  reader and the WebView in the manifest) installs `ActivityBoundaryWindowAnimations`, which points
  all four activity transition slots at the pure-alpha `activity_boundary_fade_in/out` pair —
  200ms, fast-out-slow-in, matching the policy's `CROSSFADE_DURATION_MILLIS` and the easing the
  Compose crossfade token uses. A theme reaches every start path — intent, deep link, shortcut,
  programmatic finish — with no starter cooperation, on all API levels.
- **the host owns the gesture.** `ActivityBoundaryCrossfadeHost` wraps both activities' content:
  `PredictiveBackProgress` previews the gesture with a drag-proportional dim and commits through
  `finish()`, so the *theme's* close crossfade plays instead of the system reveal. A window cannot
  reveal the activity underneath itself, so the system's cross-activity "peek" was always a mockup
  of a screen that was not on screen; the dim is honest — gesture-coupled opacity, resolved to
  zero on cancellation, and not movement.
- **no movement, enforced.** The fade XMLs are `<alpha>` only;
  `MotionConsistencyTest` fails the build on a translate/scale/rotate smuggled into them, on a
  duration that drifts from the policy, or on a missing transition slot.
- **the shared-axis window renderings stay deleted**, not orphaned: the gate fails the build if the
  reader or the WebView names an `OVERRIDE_TRANSITION` or a `shared_axis_x_` resource again, and
  fails if the four XML files come back without consumers.
- **one override survives on purpose.** `SecureActivityDelegate` launches the unlock screen with a
  zero-animation override — the lock screen neither slides nor previews, a privacy decision rather
  than a motion-language one — and the gate names it so no sweep deletes it as "the last override".
- **reduced motion.** The OS animator scale collapses the window fades on its own; the in-app
  reduce-motion preference additionally skips the gesture dim (the host reads
  `LocalMotionPreference`, which both activities provide).

The trade is recorded rather than hidden: the Android 14+ system predictive reveal no longer plays
at this boundary — the drag dim replaces it — and the boundary does not pretend to preview the
receiving tab, which an in-window animation cannot do honestly anyway. The reader→manga open path
(`openMangaScreen` into MainActivity's nav graph) is not part of this boundary; it still uses
MainActivity's motion and is a candidate follow-up, the owner's call.

For Series ↔ Library:

- the cover is the only shared element;
- the container does not move, fade, or crossfade — it is held still;
- both screens use the same background, so there is nothing to crossfade *between*;
- the series page draws no backdrop of its own;
- the cover's shape and its shared-element key are identical on both ends;
- the same model is used for predictive back and for the toolbar's back affordance;
- the back transition is **shorter** than the forward one, per the M3 shared-element spec;
- invalid shared-element bounds fall back to a clean crossfade.

## Hierarchy runs on the horizontal axis

A hierarchical destination slides on the **X** axis, not on scale. Scale (shared axis Z) is the
transition for entering or leaving a modal state; using it for ordinary forward navigation made a
push read as the screen being zoomed at rather than moved to, and made back the reverse of a zoom.

The forward leg travels a third of the viewport and the back leg is shorter, for the same reason the
shared cover's return is shorter: the user already knows where back goes. The fallback previously
composed a scale-up *and* a 10% slide at the same time, which is neither a shared axis nor a slide and
is the muddle this section exists to remove.

## Motion v2 — one policy, quick cohesion (2026-11-08)

The E3 freeze left three motion debts on the table: the undeclared-route fallback was decided in
`MainActivity` instead of the policy, the shared axis was slow enough to read as weight rather than
depth, and the `MotionTokens` vocabulary still carried M3-template members no code called. Motion v2
closes all three.

- **Every transition resolves to a plan.** `MotionPolicy` declares `MotionRoutePair.HIERARCHICAL` — the
  declared form of what the root NavHost used to hardcode. `motionPlanFor` no longer returns null; the
  NavHost slots carry no local fallback, and an undeclared push/pop is the HIERARCHICAL plan (300ms in,
  250ms back, container on the shared axis). A cover pair whose id cannot be read from either entry
  degrades to the policy crossfade — the invalid-element rule above — instead of a local shared-axis
  slide decided where no test could see it. A plan is directional, so its pop half renders the
  reversed axis: `MotionTokens.containerPopEnter`/`containerPopExit` map the SHARED_AXIS plan onto
  the pop legs (the crossfade and no-op halves are direction-symmetric). Rendering a pop with the
  push functions replays the forward slide while the plan quietly reports the back timeline — a
  regression this change's own CI caught in round 1, and the reason the pop functions exist.
- **The shared axis is quicker.** Push: a 300ms slide with a 200ms fade that starts with the slide
  (not a 450ms slide with a fade delayed 100ms in — the incoming screen used to spend the first tenth
  of its own journey invisible). Pop enter 250ms, pop exit 200ms — back is shorter than forward
  because the user already knows where back goes. The durations live in `MotionPolicy` and `MotionTokens.kt`
  only; the four `shared_axis_x_*.xml` Activity renderings ran the same numbers as the Compose legs
  until the renderings were deleted with the activity-boundary reversal (that boundary is one
  app-owned crossfade now — see "An Activity destination's motion is one app-owned crossfade"), and
  the parity gate now
  asserts the policy against the Compose legs that remain.
- **The vocabulary is pruned and gated.** The springs, tween presets, orphan duration constants and
  orphan easings are deleted (no call sites anywhere); the alias members stay because they are the
  live vocabulary of the banner, sheet, preference, search and adaptive-pane surfaces.
  `MotionVocabularyTest` fails the build if any public member of `MotionTokens` loses its last
  consumer — audit F4's exit condition, permanent. The last inline curves (`ReaderScreen`,
  `CalendarHeader`) now name tokens, behaviour-identical.

## Direction is part of the pair

The return is not the arrival played backwards. Material 3's shared-element spec is deliberately
asymmetric: the user already knows where back goes, so the cover only has to retrace its path. A
long reverse reads as sluggish rather than smooth, which is the owner-reported "awkward back" in
`REBUILD_STATUS.md` (2026-09-28).

`MotionPolicy.plan` therefore takes a `MotionDirection`, and `MotionPlan` carries it. Two facts are
kept separate on purpose:

- **the pair** — which two screens, and therefore which element is shared (`LIBRARY_SERIES` both ways);
- **the direction** — which timeline to run on it (`FORWARD` 300ms, `BACKWARD` 200ms).

A direction-free plan conflates them, and the conflation looks like a correctness property: a test
asserting "enter and exit share one duration" reads as tidiness and is in fact the defect. Direction
is a required argument rather than a defaulted one, so a call site that forgets it is a compile error
instead of a silently-wrong duration.

Under reduced motion the asymmetry has nothing to apply to and both directions collapse to instant.

## The cover's geometry must be resolved before it is shared

`MangaCover` applies `.aspectRatio(ratio)` **before** the shared-element modifier. Applied after it,
the element measures the pre-ratio box rather than the final constrained one, and because the library
cell and the series header request different sizing the flight interpolates between non-uniform
shapes — a visible squash rather than a slide. Modifier order is invisible in review and raises no
compile error, so it is gated.

## Why the series page has no cover backdrop

A blurred, alpha-faded copy of the cover behind the series header is not a shared element — it has
no counterpart in the library, because nothing in the library has one. It therefore animates on its
own schedule underneath the one element that is supposed to carry the transition.

On the way in this reads as atmosphere. On the way back it is the defect: three alphas run at once
(the shared cover shrinking into its cell, the blurry twin fading out over it, and the library grid
fading in underneath), and the two surfaces never match. The result is a transition that looks
like it is being pulled away rather than one continuous movement.

So the rule is: **one screen, one copy of the cover.** If a backdrop is ever wanted, it must be
promoted to a shared element with a counterpart on the library side, or derived from the cover's
own progress. It may not be a free crossfade.

## Container motion is a decision, not a default

`MotionPolicy` is the single owner of what a route pair does. Screens ask it and then render the
answer. A screen that reaches for a specific transition by name has re-decided the policy locally,
which is how a rule can be fully specified and fully unit tested while the layer that actually
moves things quietly does something else.

`ContainerMotion.NONE` means *nothing happens* — `EnterTransition.None`, not a fade. When the cover
carries the transition there is nothing left for the container to do, and a crossfade underneath a
travelling cover is a second competing animation for the same visual moment.

## Tokens

Durations and easing are semantic tokens, not per-screen magic numbers. A shared cover and its container must not use unrelated competing transforms.

## Reduced motion

Reduced-motion mode removes nonessential scale/slide movement and uses an instant state change or short crossfade. It must preserve semantic continuity and accessibility focus.

## Acceptance

Each transition pair records:

- source item identity;
- destination item identity;
- owner of each visual element;
- duration/easing token;
- invalid-bounds fallback;
- predictive-back behavior;
- reduced-motion behavior;
- screenshot/frame evidence.

## Gates

Three structural tests read the source for the rules above, because each one is a fact about what the
code does *not* do and no unit test can drive the transition:

- `LibrarySeriesTransitionTest` — the container is a real no-op, the cover's ratio is resolved before
  it is shared, `MainActivity` consults `MotionPolicy` rather than re-deciding, and both directions of
  the pair resolve over one predicate.
- `MotionConsistencyTest` — the Activity boundary is one app-owned crossfade: the reader and the
  WebView host `ActivityBoundaryCrossfadeHost` and provide `LocalMotionPreference`, register no
  window override and name no shared-axis window resource, the manifest declares the boundary theme
  on exactly those two activities, the fade renderings are pure alpha at the policy's crossfade
  duration and easing with all four transition slots wired, the deleted `shared_axis_x_*.xml`
  renderings stay deleted, and the unlock screen's zero-animation override is named as the one
  deliberate remaining use; the policy's HIERARCHICAL durations are the numbers
  the Compose legs play — policy and its rendering gated as one loop; every shared-cover host provides
  an animated-visibility scope; each list that opens a
  series keys its cover on the id it opens; and the undeclared-route fallback is a policy plan, not an
  activity decision — `MainActivity` names no transition token directly and no NavHost slot branches
  on a nullable plan.
- `MotionVocabularyTest` — every public member of `MotionTokens` is referenced by production code or
  kept alive transitively by a live member of the same file: add a consumer or delete it. The gate is
  the motion audit F4 exit condition, made permanent.

## Back surfaces that intercept without previewing the gesture

The app opts into predictive back (`enableOnBackInvokedCallback="true"`), so every surface that
*intercepts* back owes the user either a gesture preview or a documented reason it cannot have one.
Intercepting without participating is the worst case: the system is told to animate its own
transition, the in-app handler consumes the event, and the user sees nothing move until the end.

Surfaces are therefore in one of three states, and the third is a deliberate, recorded choice rather
than an oversight:

- **Previewed** — drives the shared helper: the bottom sheets (`PredictiveBackDraggableProgress`),
  the series page and the Activity boundaries (`PredictiveBackProgress` — the reader and the
  WebView host `ActivityBoundaryCrossfadeHost`, which previews the gesture with a drag dim and
  commits through `finish()` so the boundary crossfade plays).
- **Platform-animated** — the two `NavHost`s (the navigation library animates and previews them
  without app code).
- **State change, not a spatial move** — selection-mode exits (Library, Updates, Manga chapter list,
  Migration, Migrate), search-in-tab exits (Sources, Extensions, ExtensionsRoute), WebView history,
  and onboarding step-back. There is no surface position to animate: back flips a boolean or a
  history pointer, and inventing a translation for it would imply a spatial model the screen does not
  have. These keep a bare `BackHandler` **on purpose**.

The rule that keeps this honest is structural: `PredictiveBackGateTest` scans **every** `src/main`
Kotlin file (not just `presentation-core`) and fails if any module hand-rolls a `PredictiveBackHandler`
instead of calling the shared helper. A new surface that wants a preview must use the helper; a new
surface that cannot preview must be added to the list above.

## The in-app reduce-motion preference

Reduced motion has two sources, merged rather than one overriding the other:

- the **OS** animator scale, read from `Settings.Global.ANIMATOR_DURATION_SCALE` (a floor — it is
  always honoured);
- the app's own **Reduce motion** preference (`UiPreferences.reduceMotion()`), which exists so a user
  who dislikes *this app's* transitions does not have to disable animation for every app on the
  device.

`effectiveReducedMotion(system, app)` is the merge and is asserted directly. Under reduced motion the
navigation transitions collapse to instant, as before; the in-app preference adds no new transition
behaviour, only a second way to ask for the existing one.

