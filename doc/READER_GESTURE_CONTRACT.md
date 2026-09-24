# Reader Gesture Contract

> **Status:** binding technical contract. One viewport owns one pointer session.

## Pointer state machine

```text
Idle
  -> Down
  -> Candidate
  -> SingleScroll
  -> Transform
  -> Commit
  -> Idle

Candidate -> GestureCancelled -> Idle
Transform -> Suspended -> Transform
```

A second pointer transitions `Candidate` or `SingleScroll` directly to `Transform`.

## Ownership rules

- The viewport observes the first pointer before parent scroll/navigation consumers.
- Once a transform is claimed, the viewport owns the transform pointer stream.
- A parent may still own ordinary one-pointer scrolling only while the viewport remains undecided.
- A consumed child event cannot restart a new gesture.
- Pointer cancellation always produces `GestureCancelled`; it never becomes a tap or page navigation.

## Interaction decisions

| State | Meaning | Owner |
|---|---|---|
| Fit, one pointer | page/document navigation | viewport navigation policy |
| Fit, two pointers | pinch zoom | viewport transform |
| Meaningful zoom, dominant horizontal | pan | viewport transform |
| Meaningful zoom, dominant vertical | document scroll | viewport document policy |
| Below slop | tap candidate | viewport gesture arbiter |
| Long press without movement | long press | viewport command policy |

The scale threshold is not allowed to independently decide every interaction. Transform state is explicit.

## Transform commit

A transform may be live for pointer movement, but it is committed as a state transition with:

- focal point;
- resulting scale;
- resulting translation;
- clamp result;
- source page/document revision.

A cancelled transform restores the last committed transform. A chapter or page change invalidates the active transform.

## Double tap

Double-tap recognition is a gesture policy, not a navigation side effect. The first tap is deferred only for the configured double-tap interval. If no second tap arrives, the single command is delivered exactly once. Navigation regions do not receive an early first tap.

## Test matrix

- one pointer crossing slop;
- second pointer after parent scroll starts;
- pinch then one-finger release;
- pinch cancellation;
- parent pager competing with pinch;
- LazyColumn competing with pinch;
- double tap versus single tap;
- long press versus movement;
- transform across page/chapter boundary;
- rapid repeated pinch events.