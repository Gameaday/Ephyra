
> **Authority:** [`../ROADMAP.md`](../ROADMAP.md) is the program entry point. This document is the current source/search/discovery product contract. Historical audit files do not define the target architecture.
# Ephyra 2.0 Reconstruction Program

## 1. Purpose

This program replaces the current mixed-generation application architecture with an Android-native system whose state, rendering, persistence, navigation, and media lifecycles have explicit owners.

The program is not a visual polish initiative. It exists because current device behavior disproves the assumption that isolated fixes are sufficient: pager zoom is disconnected, continuous zoom is implemented in the wrong coordinate system, shared navigation spans two navigation owners, and reader resources are spread across mutable models, ViewModels, viewers, and Compose.

No production behavior is exempt from redesign. Existing code may be reused only after its responsibility and contract are explicitly validated.

## 2. Target qualities

### Correctness

- Invalid reader states are unrepresentable.
- One visible setting has one owner from event to pixels.
- Gesture ownership is deterministic and tested.
- Source, decoded, and render data have distinct identities and lifetimes.

### Android-native implementation

- Compose is the reader and application UI.
- Navigation Compose owns main navigation.
- A separate reader Activity is allowed for immersive/secure behavior.
- Hilt provides constructor injection.
- Coroutines/Flow manage asynchronous work.
- Room owns relational persistence.
- WorkManager owns deferrable background work.
- DataStore owns durable preferences.
- Coil remains infrastructure, not business state.
- Material 3 supplies interaction semantics and accessible component behavior.
- Macrobenchmark, instrumentation, and screenshots verify runtime behavior.

### Maintainability

- Dependencies point inward.
- Features do not depend on data implementations or sibling features.
- Cross-feature coordination belongs to the app shell.
- State changes are modeled as commands, reducers, effects, and immutable snapshots.
- High-risk engines are pure Kotlin where practical and Android adapters where necessary.

### Performance

- Network, bytes, decoded pixels, and coroutine lifetimes are bounded.
- Continuous documents use virtualization.
- Image detail is decoded for the requested scale bucket.
- The viewport is clipped once.
- No visible feature depends on `largeHeap`.
- Frame and memory budgets are release criteria.

## 3. Target architecture

```text
:app
  composition root, startup coordination, one main navigation graph

:domain
  pure models, policies, use-case contracts

:data
  Room, preferences, backup, files, persistent-store implementations

:source
  source API, adapter bridge, script/heuristic engines, orchestration

:media
  cover and page image contracts, source identity, decode plans, working caches

:reader:core
  pure session state, commands, effects, chapter policy, geometry

:reader:engine
  Android paged viewport, document viewport, gesture arbiter, tile rendering

:ui:system
  Material theme, motion, accessibility, reusable components

:ui:navigation
  typed routes, navigation coordinator, adaptive shell

:feature:library
:feature:series
:feature:updates
:feature:history
:feature:browse
:feature:downloads
:feature:settings
```

This is a dependency target, not permission to create every module immediately. Packages may be introduced first and promoted to modules only when the boundary must be independently enforced.

### Dependency law

```text
app -> features, implementations
features -> domain, public contracts, ui system
implementations -> domain/contracts
contracts never depend on implementations
```

Forbidden:

- feature  data implementation;
- feature  sibling feature, except an explicit allowlist;
- presentation hub  data API;
- domain  Android;
- UI writing persistence or resource state directly;
- Android `Activity` passed into rendering engines.

## 4. State law

Every long workflow uses:

```text
Intent/Command
    -> deterministic reducer/store
    -> immutable State
    -> typed Effect
    -> effect handler
    -> result Intent
```

Rules:

1. State contains values, not services or mutable resources.
2. Effects are explicit and cancellable.
3. Expensive asynchronous work has an owner and a bounded lifetime.
4. One fact has one canonical representation per layer.
5. Crossing a layer transforms data; it does not share mutable ownership.
6. A visible control either works, is contextually hidden, or is removed.
7. Process recreation is part of the contract for long workflows.
8. Events are delivered once or represented in state; ambiguous duplicate delivery is forbidden.

## 5. Resource law

```text
Durable source bytes
  original/downloaded content

Working source bytes
  bounded, cancellable, disposable

Decoded images
  scale-aware, memory-budgeted, disposable

Render artifacts
  viewport-owned, disposable

Durable user assets
  custom covers, settings, downloads
```

No layer may create a hidden second durable copy. Atomic replacement, corruption recovery, identity, and retention must be explicit.

## 6. Motion law

Motion is semantic:

- shared cover: one shared element only;
- hierarchical content: shared-axis or shared-element container;
- tab peers: fade-through;
- adaptive panes: directional pane transition;
- sheets/dialogs: component motion;
- reduced motion: replace nonessential movement with crossfade/instant state change.

A shared element may not share a destination with independently scaling, sliding, or fading full-screen containers. Predictive back must map to the same visual model as completed back.


## 7. Phase plan

Every phase contains: prerequisites, tasks, deliverables, verification, exit criteria, and rollback. A task may be split, but its ID and evidence requirement may not be discarded.

### Phase 0  Freeze and establish truth

**Purpose:** prevent the current manual bug-fix loop from mutating architecture before behavior is measurable.

Tasks:

- `GOV-001`: create a signed/tagged reconstruction baseline and record SHA, Gradle, JDK, and dependency state.
- `GOV-002`: mark legacy roadmaps as historical; establish this program as authoritative.
- `TST-001`: add deterministic fixtures:
  - bordered/uncropped static page;
  - crop-only border;
  - mismatched artwork corners;
  - tall page;
  - short page;
  - animated page;
  - multi-slice page;
  - corrupted page;
  - missing page;
  - landscape/portrait/compact/expanded navigation scenes.
- Capture current failing behavior as a known-bad baseline, not as expected behavior.

Verification:

- clean checkout builds;
- baseline tests and lint run;
- fixture manifest is versioned;
- no production behavior is changed.

Exit: baseline reproducible; fixtures available; open defects categorized; no untracked ambiguity.

Rollback: documentation/fixtures only; no production rollback needed.

### Phase 0A  Technical contracts

**Purpose:** convert architectural decisions into implementation-sized contracts before production replacement begins.

Contracts:

- `READER_ARCHITECTURE.md`
- `READER_GESTURE_CONTRACT.md`
- `DOCUMENT_VIEWPORT_CONTRACT.md`
- `MEDIA_PIPELINE_CONTRACT.md`
- `NAVIGATION_CONTRACT.md`
- `MOTION_NAVIGATION_CONTRACT.md`
- `SOURCE_DISCOVERY_ARCHITECTURE.md`
- `SOURCE_DISCOVERY_EXECUTION.md`
- `FIXTURE_MANIFEST.md`
- `PERFORMANCE_BUDGETS.md`
- `DEPENDENCY_TARGET_GRAPH.md`

Task: `CON-001`.

Verification:

- every current contract is linked from the roadmap;
- every contract names owner, state/resource lifetime, failure/cancellation path, and evidence;
- task IDs are implementation-sized and dependency-ordered;
- no production code claims a replacement is complete from architectural prose alone.

Exit: a new contributor can implement a contract task without inferring ownership or acceptance from legacy source.

### Phase 1  Security and platform safety

**Purpose:** remove risks independent of the reconstruction.

Tasks:

- `SEC-001`: remove hardcoded signing passwords/paths from Gradle; use environment/CI secrets.
- `SEC-002`: audit all persisted credentials, cookies, OAuth tokens, source headers, and logs; move secrets to a Keystore-backed store.
- `SEC-003`: remove `largeHeap` after measuring the current baseline; remove battery-optimization exemption unless a documented WorkManager limitation requires it.
- Audit exported components, deep links, WebView isolation, incognito network handling, backup exposure, and log redaction.

Verification:

- secret scanner;
- manifest and permission policy tests;
- backup inspection ensures secrets are excluded;
- no plaintext credentials in DataStore/Room/logs;
- low-memory smoke test plan exists.

Exit: no known critical security finding; every accepted exception has an owner, reason, and review date.

### Phase 2  Quality infrastructure

**Purpose:** make Android behavior executable before replacing the reader.

Tasks:

- `TST-001A`: define the executable reader/media fixture catalog and interaction registry.
- `TST-001B`: generate deterministic synthetic static media artifacts and bind them to decoder, crop, and slicing tests.
- `TST-002`: add a connected instrumentation module for reader, navigation, cache, migration, and WorkManager smoke tests.
- `TST-003`: add deterministic screenshot tests and macrobenchmark execution.
- Define device matrix:
  - API 26 or minimum supported API;
  - current target API;
  - one low-memory physical/virtual device;
  - one high-refresh physical device if available;
  - compact phone;
  - foldable/expanded layout.
- Add CI jobs for lint, unit tests, instrumentation, screenshots, and benchmarks.
- Make release gate failures visible and non-bypassable.

Verification:

- CI artifacts include test reports and benchmark results;
- a deliberately broken fixture fails the expected gate;
- no device unavailable path silently reports success.

Exit: reader and navigation behavior can be proven or explicitly marked pending.

### Phase 3  Dependency and state foundations

**Purpose:** remove structural leakage before building new features.

Tasks:

- `ARC-001`: define the target module graph and enforce it in Gradle plus architecture tests.
- Move image request contracts out of `presentation-core` into a contracts/media module.
- Remove `api(projects.core.data)` from `presentation-core`.
- Convert feature-to-feature dependencies into explicit allowlisted navigation contracts.
- `ARC-003`: create the base effect/state pattern and migrate one simple feature as the reference implementation.
- Replace global mutable navigation event objects with an app navigation coordinator.

Verification:

- feature modules have no data imports/dependencies;
- sibling-feature dependencies are allowlisted;
- domain remains Android-free;
- reference feature has deterministic reducer tests.

Exit: target boundaries are enforced; no new feature can violate them unnoticed.




### Phase 4  Media source and image planning

**Purpose:** make image behavior a typed pipeline before building viewports.

Tasks:

- `MED-001`: define `PageSource`, source identity/revision, source metadata, content rectangle, animation kind, and decode plan.
- Separate remote, file, archive, and memory source adapters behind one semantic contract.
- Move Coil configuration out of feature modules; UI requests semantic images, not raw request construction.
- Define crop behavior for static, animated, unsupported, and corrupt images.
- Define scale-bucket decode sizing and hardware/software fallback.
- Preserve cover-cache ownership while separating cover and page policies.

Verification:

- contract tests for every source adapter;
- deterministic synthetic bitmap tests;
- animated crop policy tests;
- request identity tests;
- corrupt/partial source recovery tests.

Exit: every image has stable identity, known dimensions, transform policy, and bounded decode behavior before entering a viewport.

### Phase 5  Bounded working and tile caches

**Purpose:** prevent memory growth and make image reuse explicit.

Tasks:

- `MED-002`: move page bytes out of `ReaderPage`; create a byte-budgeted working source.
- Keep durable page bytes in the chapter/download store.
- Add a decoded-tile cache with byte budget, LRU behavior, cancellation, and scale buckets.
- Implement progressive continuous-page decoding: visible tile, adjacent prefetch, distant eviction.
- Ensure bitmap references are not recycled while Compose may still read them.
- Add metrics for bytes, tiles, hit/miss, decode time, and fallback path.

Verification:

- long-chapter memory test;
- low-memory callback test;
- cancellation test;
- tile partition/continuity property tests;
- cache hit/miss and invalidation tests.

Exit: no unbounded page byte or bitmap ownership remains in the active reader session.

### Phase 6  Pure reader core

**Purpose:** make reader behavior deterministic without Android.

Tasks:

- `RDR-001`: define `ReaderState`, `ReaderCommand`, `ReaderEffect`, and `ReaderSession`.
- `RDR-002`: implement chapter window, canonical ordering, previous/next eligibility, forward skip-read, duplicate policy, and downloaded-only policy as pure functions.
- Define process recreation contract for chapter/page/menu/zoom settings.
- Separate page metadata from page resources.
- `RDR-003`: implement gesture arbitration as a pure state transition plus a thin Android adapter.

Verification:

- reducer table tests for every command;
- chapter navigation matrix;
- retry and resource invalidation tests;
- rotation/process recreation tests;
- pointer transition tests with synthetic event sequences.

Exit: the entire reader session can be simulated deterministically on the JVM.

### Phase 7  Paged reader

**Purpose:** deliver correct page fit, pinch, pan, and navigation.

Tasks:

- `RDR-004`: implement paged viewport using one gesture arbiter.
- At fit scale, page swipe belongs to the page navigator.
- At meaningful zoom, pan belongs to the viewport.
- Pinch uses centroid-based focal zoom and scale limits.
- Double tap toggles fit and a documented zoom level.
- Decode detail for the current scale bucket.
- Preserve page position through rotation and process recreation.
- Support LTR, RTL, tall-page, short-page, local, online, and animated inputs.

Verification:

- E3 pointer tests for fit/pinch/pan/swipe;
- screenshot tests for every page type;
- E4 low-memory and high-refresh checks;
- no pager swipe regression at fit.

Exit: paged reader behavior is independently accepted before continuous mode begins.

### Phase 8  Continuous reader

**Purpose:** replace per-item webtoon transforms with a document viewport.

Tasks:

- `RDR-005`: implement `WebtoonDocument` and `WebtoonViewport` in document coordinates.
- Render visible pages/tiles through a single viewport clip.
- Use scale buckets and progressive tile decode.
- Do not mutate LazyColumn item height for zoom.
- Decide whether a LazyColumn is used only for logical chapters; otherwise use a custom virtualized canvas.
- Preserve chapter transitions and reverse scrolling.
- Reset or restore page-local viewport state by explicit policy.
- Provide width-only fallback only if product requirements demand it; do not call horizontal stretching zoom.

Verification:

- tile partition property tests;
- visible-region tests;
- pinch focal-point tests;
- scroll-after-zoom tests;
- no-overlap/no-gap screenshot sequences;
- long-document memory benchmark.

Exit: continuous zoom reveals real detail, remains stable during scroll, and respects one clip/coordinate system.



### Phase 9  Application shell and navigation

**Purpose:** establish one navigation and adaptive-layout owner.

Tasks:

- `NAV-001`: move Library, Updates, History, Browse, More, Series, and settings destinations into one main `NavHost`.
- Keep the reader in a separate Activity only if immersive/secure lifecycle requirements justify it.
- Use typed destinations only; delete duplicate string route hierarchies.
- Put bottom bar/rail visibility in the shell, not global singleton state.
- Add back handling, deep links, and process restoration through one navigation coordinator.
- Make compact, expanded, foldable, and tablet layouts explicit.

Verification:

- navigation graph contract tests;
- back-stack and deep-link tests;
- compact/expanded screenshot tests;
- predictive-back smoke test.

Exit: no feature navigates through a sibling feature implementation or a second hidden graph.

### Phase 10  Library and Series vertical slice

**Purpose:** prove the first complete user journey before migrating other features.

Build the complete path:

```text
Library -> Series -> Reader -> Back -> Series -> Back -> Library
```

Tasks:

- `NAV-002`: implement the shared-cover policy and stable non-cover container.
- No whole-screen scale competing with the shared cover.
- Metadata/content uses a clean crossfade.
- Predictive back maps the same visual transformation.
- Missing cover, invalid bounds, duplicate grid item, and offscreen item use a deterministic fallback.
- Integrate persistent covers and offline behavior.

Verification:

- E3 transition sequence tests;
- E4 predictive-back matrix;
- screenshot sequence on compact and expanded layouts;
- frame/jank benchmark;
- offline/re-entry test.

Exit: the transition is accepted as a coherent visual system, not merely a cover animation.

### Phase 11  Remaining product slices

Migrate complete vertical slices in this order:

1. Updates.
2. Browse and source discovery.
3. Search and source results.
4. Downloads and local content.
5. History.
6. Settings.
7. Tracking.
8. Migration and backup.
9. WebView/player platform surfaces.

For each slice define, before coding:

- user entry points;
- loading/empty/error/offline states;
- navigation destinations;
- persisted state;
- accessibility actions;
- screenshots;
- performance budget;
- removal plan for the old implementation.

Exit: product parity checklist is complete; no old screen is reachable merely because it has not been migrated.

### Phase 12  Source, search, discovery, data, startup, and worker hardening

The target product contract is [`SOURCE_DISCOVERY_ARCHITECTURE.md`](SOURCE_DISCOVERY_ARCHITECTURE.md). The Tachiyomi/Mihon API is a temporary compatibility boundary, not the source model.

Tasks:

- `SRC-000`: inventory every source adapter, capability, legacy type, service-locator dependency, and user-visible source/search entry point; classify each as target, adapter, or delete.
- `SRC-001`: introduce `SourceDescriptor`, capability declarations, `SourceGateway`, and typed outcomes: `Success`, `Empty`, `Unsupported`, `TransientFailure`, `PermanentFailure`, `RateLimited`, and `Cancelled`.
- `SRC-002`: implement progressive, cancellable search sessions with bounded concurrency, deadlines, partial results, provenance-preserving deduplication, deterministic ranking, and explicit retry.
- `SRC-003`: implement capability-gated discovery surfaces, source health, quarantine, recommendations, and explainable ranking. Empty results must not be treated as failure.
- `SRC-004`: make migration and source replacement explicit, confidence-scored, reversible, progress-preserving, and non-destructive by default.
- `SRC-005`: isolate credentials, trust, install, and permission lifecycle from source execution; use Keystore-backed storage for secrets.
- `DATA-001`: choose clean-slate schema or explicitly supported migration path. With no compatibility constraint, prefer a clean schema v1 plus explicit backup import.
- `OPS-001`: consolidate `StartupTracker`/`StartupGuard`; split `Application` responsibilities.
- Audit every WorkManager job for constraints, retry policy, cancellation, idempotence, and user visibility.
- Audit DataStore writes for completion, error propagation, and readiness.
- Remove fire-and-forget preference writes from critical workflows.

Verification:

- capability matrix for every adapter;
- typed-outcome and empty/fallback tests;
- cancellation, deadline, partial-result, retry, and rate-limit tests;
- deterministic deduplication and ranking tests;
- provenance and source-health tests;
- zero-legacy-source startup and product-path tests;
- migration confidence, rollback, and progress-preservation tests;
- backup/restore contract suite;
- WorkManager smoke tests;
- startup state tests;
- process-death and migration tests.

Exit: the app is useful without legacy extensions; source/search/discovery behavior is capability-aware, bounded, typed, explainable, and device-verified; no source/search state is implicitly owned by legacy types or UI classes.

### Phase 13  Release engineering and legacy deletion

Tasks:

- `REL-001`: require Spotless, architecture, unit, lint, instrumentation, screenshot, benchmark, and release artifact gates.
- Publish signed artifacts only from protected CI.
- Verify API-level matrix, low-memory behavior, and OEM-sensitive integrations.
- `CLEAN-001`: delete legacy `Viewer` classes, old reader state, old route hierarchy, duplicate navigation events, dead image extras, old transitions, obsolete migrations, and the legacy source bridge once the source removal gate is satisfied.
- Remove old feature modules only after parity and navigation tests pass.
- Delete or archive superseded documents according to [`DOCUMENTATION_GOVERNANCE.md`](DOCUMENTATION_GOVERNANCE.md); do not leave competing “completed” plans in the current tree.

Verification:

- no legacy reader/navigation imports;
- no duplicate destination ownership;
- no unbounded preloading;
- no `largeHeap` without an accepted ADR;
- all release gates green;
- clean release build and reproducible artifact.

Exit: the new architecture is the only production architecture. The old system is deleted, not maintained in parallel.



## 8. Cross-cutting verification matrix

Every user-visible slice must answer all questions below:

### State

- What is the single source of truth?
- What happens on duplicate event delivery?
- What happens after process death?
- What happens on rotation/display-size change?
- What is cancelled when the screen leaves?

### Resources

- What bytes are durable?
- What decoded memory is disposable?
- What is the byte budget?
- What happens under memory pressure?
- What happens if a source is corrupt or partially written?

### User experience

- What are the loading, empty, offline, rate-limited, and error states?
- Does every visible control have an effect?
- Is the feature usable with keyboard/switch access and large text?
- Are touch targets and focus order correct?

### Android integration

- Does it work at minimum and target API?
- Does it survive background/foreground transitions?
- Does WorkManager cancellation propagate?
- Does WebView/SAF/notification behavior work?
- Is predictive back correct?

### Performance

- What are the frame, startup, memory, network, and disk budgets?
- What is measured on low-memory hardware?
- Are caches bounded and observable?
- Is the implementation free of unnecessary recomposition or image upload?

## 9. Definition of Done

A task is done only when:

1. Its contract is written and linked.
2. Implementation follows the accepted ADR.
3. Pure logic has unit tests.
4. Android behavior has instrumentation or screenshot coverage where applicable.
5. Loading, error, cancellation, recreation, and accessibility paths are handled.
6. Cache/resource ownership is documented if changed.
7. No forbidden dependency or legacy bypass is introduced.
8. Formatting, lint, compilation, and affected tests pass.
9. Device-required evidence is attached or explicitly marked pending.
10. `doc/REBUILD_STATUS.md` is updated in the same change.
11. The commit contains one coherent concern.
12. The handoff names unresolved risks and the next executable task.

## 10. Rollback policy

- Every phase must be revertible without losing unrelated user data.
- Schema changes must be additive until the clean-slate policy is explicitly selected.
- Cache replacement must never delete the last valid copy before the new copy is published.
- Navigation changes must preserve deep links or provide an explicit migration.
- Reader state changes must persist progress before releasing a chapter.
- A failed migration or restore must leave the previous valid database/backup intact.
- No rollback may use destructive database fallback.

## 11. Anti-hubris checks

Before declaring a new abstraction necessary, answer:

1. Which concrete state or dependency does it isolate?
2. Can the implementation be replaced without changing callers?
3. Is it smaller than the coordination it removes?
4. Does it have a pure test seam?
5. Does it avoid becoming a new service locator or global registry?
6. Is the problem actually ownership, or merely naming?
7. Would deleting the abstraction make the code clearer?

Before declaring an optimization necessary, answer:

1. What measurement proves the bottleneck?
2. What is the memory/network/frame budget?
3. What is the simplest implementation that meets it?
4. What is the rollback if the assumption is wrong?

Before adding a feature setting, answer:

1. What user problem does it solve?
2. What is its default?
3. What are its contextual restrictions?
4. What is its observable effect?
5. How is it tested?
6. What happens when its implementation is unavailable?

## 12. Decision log

Architectural decisions that affect multiple phases belong in `doc/adr/`. Do not silently change an accepted ADR in implementation code. If a decision is wrong, write a superseding ADR explaining:

- what evidence invalidated it;
- what harm it caused;
- what replaces it;
- which tasks and code are affected.

## 13. Program completion

The program is complete only when:

- all phases in the status ledger are `VERIFIED`;
- all required ADRs are accepted or explicitly superseded;
- release CI runs the complete gate on the declared device matrix;
- the new reader and navigation are the only production paths;
- no critical security, data-loss, accessibility, or memory finding remains;
- the release artifact is reproducible and signed;
- historical documentation is clearly marked historical.
