# Reader Modular Redesign Audit — Findings and Staged Plan

> **Status:** audit + decision record, docs-only. Read at `10f9bd76` (main, post-C7) on 2026-11-08.
> **Trigger:** owner priority — the reader half of "improve the reader and navigation?" (the navigation
> half was C7 / Motion v2, PR #330). This document records what a ground-up modular redesign of the
> reader would actually change, staged so each stage lands independently with its own tests.
> **Evidence:** E1 source inspection. No production code changed. No JDK/Android SDK is available
> locally, so remote CI is the verification channel for this docs change; **no device evidence is
> claimed** — every finding cites the file and line it was read from at the recorded commit.
> **Update 2026-11-08 (later the same day):** Stage 1 (R-B) has landed as PR #332 under the owner's
> go-ahead - section 3 F4 carries the dated inventory correction, and the Stage 1 entry in section 4
> records what shipped. Stage 2 (R-D) landed the same way as PR #333 - section 3 F6 and the Stage 2
> entry in section 4 record what shipped. Stage 3 has begun as proposed - its first coordinator
> (`ChapterLoadingCoordinator`) landed as PR #334; section 3 F3 and the Stage 3 entry in section
> 4 record what shipped. The rest of Stage 3 and Stage 4 remain proposals.

This is an audit and a plan, not a record of completed work. Section 2 is what already landed;
nothing in Section 4 has begun.

---

## 1. Verdict up front

The reader's load-bearing seams are **real, production-wired, and gated** — the A-track built them,
and the D4 re-audit already corrected the classification errors around them. A ground-up redesign is
therefore **not** a re-architecture of the reading surface: the slice renderer, gesture arbitration,
the zoom transform, the byte budget and the session model already have the shapes the contracts
specify. Nothing in this plan re-does them.

The remaining debt is concentrated in **three owners**, and it is ownership debt, not correctness
debt — no finding below is a live defect:

1. **`ReaderViewModel`** (`B-008`, confirmed below): 1645 lines, 25 constructor dependencies, six
   responsibilities the program has already separated everywhere else.
2. **`ReaderActivity` + `ReaderScreen`**: color-filter and brightness settings each render through
   two paths (the Compose overlay and the View paint/window path), and the Activity is inside the
   rendering path by cast.
3. **`ReaderPage`**: still a mutable resource carrier, which blocks the `MED-002` end-state ("UI
   renders immutable snapshots") no matter how well the byte budget is bounded.

Two ledger rows are corrected in place by this change (`B-009` is superseded; `B-010`'s harm is
bounded by design), and one stale sentence in `DOCUMENT_VIEWPORT_CONTRACT.md` is corrected: the
gesture arbiter's "remaining wiring" landed with the A4 / `RDR-005` cutover and is production-wired
in both viewers.

## 2. What already landed (the audit's baseline, not its work)

| Seam | Owner (production `src/main`) | Gate |
|---|---|---|
| Session lifecycle | `ReaderSessionCoordinator` + `ReaderSessionEffectLog` (A2) | `ReaderSessionCoordinatorTest` |
| Chapter window selection | `ReaderChapterWindowAdapter` (A2, RDR-002) | `ReaderChapterWindowAdapterTest` |
| Slice geometry + decode sample | `WebtoonSlicePlanner` (A1) | `WebtoonSlicePlannerTest` (13) |
| Zoom transform (one container `graphicsLayer`) | `WebtoonZoomGestureHost` (A4) | `WebtoonZoomInjectionTest` |
| Gesture arbitration | `ReaderGestureArbiter` + `ReaderGesturePointerAdapter` | `WebtoonGestureArbiterTest` |
| Encoded-byte budget | `PageByteStoreOwner` + `PageByteBudget` (MED-002) | `BoundedPageByteStoreTest` |
| Load recovery (CDN swap, backoff, URLs) | `HttpPageLoader` + `PageLoadRecovery` | `HttpPageLoaderCdnSwapTest` |

## 3. Findings (E1, cited at `10f9bd76`)

### F1 — `B-008` confirmed: `ReaderViewModel` spans six responsibilities

`ReaderViewModel.kt` measures 1645 lines with 25 constructor dependencies (`ReaderViewModel.kt:100-124`).
The spans separate cleanly, which is what makes the Stage 3 split mechanical rather than archaeology:

- session: `ReaderSessionCoordinator` field (`:209`), `recordSessionSelection` (`:908-941`);
- chapter loading and preload: `loadNewChapter` (`:565`), preload (`:631`), `tryPreloadNextChapterImages`
  (`:762`);
- downloads and remove-after-read: `downloadNextChapters` (`:778`), `cancelQueuedDownloads` (`:821`),
  `enqueueDeleteReadChapters` (`:1582`);
- history, tracking, read timer: `restartReadTimer` (`:1105`), `updateHistory` (`:1112`),
  `updateTrackChapterRead` (`:1568`);
- platform image I/O: `saveImage` (`:1352`), `shareImage` (`:1410`), `setAsCover` (`:1448`), `blockPage`
  (`:1476`), `findMatchingBlockedHash` (`:1523`) — these members hold `Application`, `ImageSaver` and
  `SaveImageNotifier`;
- UI state: `showMenus` (`:1312`), dialog visibility (`:1320-1340`), `brightnessOverlayValue` (`:1344`);
- viewer-mode resolution (`:1195-1300`).

Consequence: every seam the program builds lands as one more field and one more delegation inside
the same object, so the seams exist but the object still spans everything. The 25-deep constructor is
the compile-time symptom: no reader state can be exercised in a test without the platform.

### F2 — `B-009` is stale and superseded

The row claimed the continuous reader transforms `LazyColumn` item geometry and paint. It does not:
one `graphicsLayer` transform is applied to the scroll container. `WebtoonZoomState`'s KDoc states
"The transform is owned by the chapter, not by an individual page item", and `WebtoonZoomSurface`
records that item heights and scroll position are untouched by zoom — the `DEF-003` guarantee,
gated by `WebtoonZoomRenderTest.sliceBoundariesMoveWithTheDocumentTransform`. The ledger row is
corrected in place by this change (strikethrough + dated note), not deleted.

### F3 — `B-010`'s fact stands, but the harm is bounded by design

`preloadAllPages` (`HttpPageLoader.kt:575`) can still enqueue an entire online chapter — the row's
literal claim is true. What changed since the row was written is the queue's shape: only `Queue`-state
pages are enqueued, at `BACKGROUND_PRELOAD_PRIORITY = -1` (below nearby-page work at 0 and the
current page at 1), and `shouldYield`/`requeueAndYield` preempt background work when foreground
reading needs the worker. The call site (`ReaderViewModel`'s chapter-load path) carries a rationale
comment. A dated note is added to the row rather than deleting it: the row is the record of why the
call site exists, and Stage 3's loading coordinator re-owns it.

> **Update 2026-11-08 (Stage 3 seam 1 landed, PR #334):** the call site is re-owned.
> `ChapterLoadingCoordinator` decides the open/preload sequence and owns the buffer-health rule
> (previously counted twice - in `preload()` and `tryPreloadNextChapterImages()`); the view model
> drives it through `ChapterLoadingEffectHandler` and no longer inlines the count (gated by
> `ChapterLoadingOwnershipTest`).

### F4 — color filter and brightness each render through two paths

- Color filter: `ReaderScreen`'s `ContentOverlay` (Compose, `BlendMode`) and
  `ReaderActivity.getCombinedPaint` (`ReaderActivity.kt:537`, `ColorMatrix`) applied by `setLayerPaint`
  (`:651`, `LAYER_TYPE_HARDWARE`).
- Brightness: window attributes via `setCustomBrightness` (`:633`) and `brightnessOverlayValue`
  (`ReaderViewModel.kt:1344`, rendered as a Compose overlay).

Which path actually renders a given setting depends on which viewer hosts the surface — a drift
risk, not a defect today. One user setting should have one owner from event to pixels; Stage 1
decides the owner and gates the single path. `ReaderActivity` (about 657 lines: viewer creation,
paint, insets at `:519`, `setKeepScreenOn` at `:625`) is where the View-side halves live.

> **Corrected 2026-11-08 (Stage 1 inventory, E1 at `0b3dd1bf`):** both halves were wrong in
> opposite directions, so "drift risk between two live paths" overstated the View side and
> understated the brightness side. The View half was never a live render path: every
> `Viewer.getView()` returns an empty `View(activity)` fallback ("actual rendering is handled
> natively in Compose"), so the `ColorMatrix` paint was applied to a view that draws nothing -
> dead since the Compose migration, not a competing implementation. The brightness Compose half
> was dead too: `State.brightnessOverlayValue` was fed only by
> `ReaderEvent.SetBrightnessOverlayValue`, which nobody emitted, so the scrim never drew and the
> field stayed `0` while negative slider values wrote an undefined negative float into
> `screenBrightness`. Stage 1 (section 4) deleted the View half and wired the Compose half
> reactively.

### F5 — `ReaderPage` is still a mutable resource carrier

`ReaderPage.kt` (119 lines) holds a `stream` lambda (`:11`), `mergedBitmap` (`:27`), mutable
`width`/`height` (`:88`/`:91`), `cachedBytes` (`:100`), with `@Volatile` throughout. `MED-002` bounded
the bytes (`PageByteStoreOwner`/`PageByteBudget`), but the model still crosses every layer carrying
working bytes and decoded pixels. "UI renders immutable snapshots" is not satisfiable while the
model has this shape; this is why Stage 4 exists and why it is last.

### F6 — the Activity is inside the rendering path by cast

`ReaderScreen` (419 lines) casts `context as? ReaderActivity` and routes viewer callbacks through it
(`activity::onPageSelected` and friends). A callback interface removes the cast without changing
viewer behaviour (Stage 2). The same file hosts an `AndroidView` fallback for non-Compose viewers —
which `Viewer` implementations still need the View path is the Stage 1 inventory question, and its
answer decides whether the View paint path can be retired or must be kept for the fallback alone.

> **Update 2026-11-08 (Stage 2 landed, PR #333):** the cast is gone. `ReaderScreen` takes
> `ReaderViewerHost`, a four-method callback interface, and `ReaderActivity` implements it by
> delegating to the same members - every callback lands where it did. The fallback question this
> section asked was answered by Stage 1's inventory (no `Viewer` implementation needs the View
> path; the branch stays as an interface safety net).

### F7 — UI reads page payload bytes directly

`ReaderScreen`'s automatic-background decision reads
`state.currentChapter?.pages?.getOrNull(currentPage)?.cachedBytes` (around `:106`) — UI reading a
page's working bytes rather than a rendered-surface signal. Same root as F5; resolved by Stage 4.

## 4. Staged redesign (decision record; Stages 1-2 done 2026-11-08; Stage 3 seam 1 done 2026-11-08; the rest proposals)

- **Stage 0 (this change, docs-only):** this audit; `B-009` superseded and `B-010` bounded in the
  ledger; the viewport contract's stale gesture sentence corrected; this document indexed.
- **Stage 1 (R-B) — one overlay owner. DONE 2026-11-08 (PR #332, E1 + CI):** the inventory found the
  View paint path was never a live path - every `Viewer.getView()` returns an empty
  `View(activity)` fallback, so the `ColorMatrix` was applied to a view that draws nothing. It is
  deleted, not migrated: `applyColorLayerPaint`, `getCombinedPaint`, `setLayerPaint`, their
  collectors and the `updateViewer()` tail call are gone. The Compose `ReaderContentOverlay` is
  the single pixel owner - brightness is now read reactively from the preferences (the relayed
  `State.brightnessOverlayValue` was fed by an event nobody emitted), and `setCustomBrightnessValue`
  honours the slider's documented contract (negative -> `0f` minimum + scrim, 0 -> system,
  positive -> `value/100f`). `trueColor`/`grayscale`/`invertedColors` are read by nobody; restoring
  night-mode invert as a Compose effect remains the owner decision it always was. Gated by
  `ReaderRenderOwnershipTest`. The `AndroidView` fallback branch stays (interface safety net);
  `Viewer.getView()` retirement is Stage 2/4 scope.
- **Stage 2 (R-D) - callbacks replace the cast. DONE 2026-11-08 (PR #333, E1 + CI):** the interface
  is `ReaderViewerHost` - four methods (`onPageSelected`, `onPageLongTap`, `toggleMenu`,
  `requestPreloadChapter`), exactly the set the Compose viewer surfaces invoke. `ReaderScreen` takes
  the interface and routes all four callbacks through it; the `context as? ReaderActivity` cast is
  deleted, so the rendering path no longer knows the concrete host class and can no longer silently
  not render because a cast returned null - no production context produced one (the screen is
  composed only by `ReaderActivity`, which implements the contract by delegating to the same
  members). No viewer behaviour change. Gated by `ReaderViewerHostOwnershipTest`, including the
  unwired-contract rule: an interface member the screen does not route fails the build.
- **Stage 3 (R-A) — split `ReaderViewModel` per seam.** Coordinators land one seam at a time, each
  with its own tests; `ReaderSessionCoordinator` (A2) is the template — a coordinator the VM drives,
  with an effect log as the seam. Proposed order: `ChapterLoadingCoordinator` (loading, preload,
  recovery — the F3 call site), `ReaderImageActions` (platform image I/O), `ReaderDownloadPolicy`
  (download-next, remove-after-read), `ReaderProgressRecorder` (history, tracking, read timer),
  `ViewerModeResolver` (`:1195-1300`). Menu/dialog state stays in the VM — it is genuinely view
  state. Names are proposals.
  - **Seam 1 done 2026-11-08 (PR #334, E1 + CI): `ChapterLoadingCoordinator` (loading, preload,
    recovery - the F3 call site).** The view model drives it; the coordinator owns the open
    ordering (session open -> page load -> filter wiring -> background queue -> window install
    -> history -> read timer -> session report), the preload eligibility ladder, the
    downloaded-chapter promotion, the buffer-health gate and the adjacent-start plan. The
    forward-buffer count previously existed twice (`preload()` and
    `tryPreloadNextChapterImages()`); it now has one home (`pendingAheadOf`). No behaviour
    change: every rule is the code that already ran, moved not rethought. Gated by
    `ChapterLoadingCoordinatorTest` (ordering, eligibility, buffer-health, adjacent-start
    tables) and `ChapterLoadingOwnershipTest` (the view model drives the coordinator; the
    count has one home; every driven effect is implemented).
- **Stage 4 (R-C) — `ReaderPage` becomes immutable metadata + a `PageResource` handle.** Bytes and
  merged bitmaps move behind the chapter loader/resource owner; completes `MED-002` and resolves
  F5/F7. Deliberately last: it touches the loaders, smart-merge, the pre-processor and both viewers,
  and doing it before Stage 3 exists would re-plumb the same seams twice.

**Not decided here:** `A5` device evidence (device-bound, owner call), any viewer behaviour change,
retirement of the View fallback (Stage 1 inventory first), and the Stage 3 coordinator names/shapes
(proposed, not fixed).

## 5. What this audit does not claim

- It is **not** a claim that the reader misbehaves today: every finding is ownership or shape debt;
  no live defect was found, and no behaviour change is proposed before Stage 1.
- **No device evidence:** E1 source inspection only; line citations reference `10f9bd76` and drift
  with the tree.
- **No local build:** docs-only change; remote CI is the verification channel for this commit.
- The staged plan is a **proposal for owner review**. It is sequenced, each stage is
  independently landable, and the stages are reversible individually. **Update 2026-11-08:** the
  owner's go-ahead started execution; Stage 1 (R-B) and Stage 2 (R-D) have landed (PR #332,
  PR #333, CI-verified), and Stage 3 has begun - its first coordinator (`ChapterLoadingCoordinator`)
  landed as PR #334, CI-verified. The rest of Stage 3 and Stage 4 remain proposals and nothing
  in them has begun.
