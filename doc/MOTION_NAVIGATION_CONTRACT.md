# Motion and Navigation Contract

> **Status:** binding technical contract. Motion is assigned by semantic route pair, not globally.

## Assignment matrix

| Route pair | Cover | Non-cover content | Back |
|---|---|---|---|
| Library ↔ Series | Shared cover only | Held still (no fade) | Same model, shorter timeline |
| Any cover list ↔ Series | Shared cover only | Held still (no fade) | Same model, shorter timeline |
| Tab peer | None | Directional horizontal slide | Tab history model, same slide reversed |
| Hierarchical destination | Optional shared element | Shared axis X | Reverse shared axis X |
| Activity destination (reader, WebView) | None | Shared axis X, same tokens | Reverse shared axis X, shorter |
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

## An Activity destination uses the same tokens as a Compose one

The reader (and the WebView) are separate Activities, so they cannot use the Compose transition. The
window animation is the only motion available, and it must be the same movement: same travel, same
easing, same durations.

Two rules follow:

- **both halves are set.** A destination that overrides only its close transition cuts on the way in
  and slides on the way out. `ReaderActivity` did exactly that — `OVERRIDE_TRANSITION_OPEN` was
  never registered — which is invisible in review because the code that *is* present looks correct.
- **the XML is a rendering of the token, not a second definition.** Travel is `SHARED_AXIS_X_TRAVEL`;
  the four `shared_axis_x_*.xml` files moved 5% of the viewport over a flat 300ms while the Compose
  token moved 30% with an asymmetric fade, so pushing into the reader was a smaller gesture than
  pushing into a nav destination. The distance is now asserted against the token in both places.

Reduced motion needs no branch in the Activity path: the platform honours the animator scale and
collapses these to an instant change on its own.

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

Two structural tests read the source for the rules above, because each one is a fact about what the
code does *not* do and no unit test can drive the transition:

- `LibrarySeriesTransitionTest` — the container is a real no-op, the cover's ratio is resolved before
  it is shared, `MainActivity` consults `MotionPolicy` rather than re-deciding, and both directions of
  the pair resolve over one predicate.
- `MotionConsistencyTest` — the reader animates both halves; the Activity animations travel the token
  distance; every shared-cover host provides an animated-visibility scope; and each list that opens a
  series keys its cover on the id it opens.