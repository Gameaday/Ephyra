# Motion and Navigation Contract

> **Status:** binding technical contract. Motion is assigned by semantic route pair, not globally.

## Assignment matrix

| Route pair | Cover | Non-cover content | Back |
|---|---|---|---|
| Library ↔ Series | Shared cover only | Crossfade | Same reverse model |
| Tab peer | None | Fade through | Tab history model |
| Hierarchical destination | Optional shared element | Shared axis | Reverse shared axis |
| Compact pane → expanded pane | Optional item identity | Directional pane | Directional reverse |
| Sheet/dialog → parent | None | Component motion | Parent state |

For Series ↔ Library:

- the cover is the only shared element;
- source/destination non-cover content crossfades;
- full-screen content does not independently scale or slide;
- the same model is used for predictive back;
- invalid shared-element bounds fall back to a clean crossfade.

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