# Ephyra Design Principles

> **"Ephyra is a system of discrete, replaceable systems connected only by interfaces.
> Each system performs exactly one responsibility. Data flows in one direction. State is
> observable. Side effects are explicit. Every boundary is testable in isolation."**

This document is the single source of truth for architectural decisions in Ephyra. It is
not aspirational — it is the law. Every code review, every new feature, every refactor
must be measured against these principles. If the code you are writing cannot be
explained by one of these principles, stop and ask why.

---

## The Core Philosophy: Unix-Style Systems

The Unix philosophy applied to Android: **small tools, sharp edges, connected by
well-defined pipes.** A feature module must not care how the database works. The database
must not care how extensions are loaded. The UI must not care how data is fetched. The
only thing each layer knows about its neighbours is the interface contract between them.

This is how we build software that can be maintained, improved, and extended for 20 years.

---

## Principle 1 — The Dependency Rule Is Absolute

Dependencies always point **inward** toward the domain. Never outward. Never sideways.

```
feature → domain (interfaces only) ← data (implementations)
feature → presentation-core
app (wires everything together)
```

The `:core:domain` and `:domain` modules must have **zero** `android.*` imports. They
must compile as pure Kotlin, testable on the JVM without an Android runtime. If a domain
class imports `android.*`, that is an architectural violation that must be resolved before
merging.

**Enforced by:** the "Architecture fitness" step in `build.yml` which fails the CI build
on any `android.*` import in domain source trees.

---

## Principle 2 — Module Boundaries Are Interface Boundaries

Every module exposes **only interfaces** (defined in `domain`) and receives **only
interfaces**. Concrete implementations live in `:data` and `:app`. No feature module ever
imports a concrete repository or service implementation directly.

The canonical pattern is:

```kotlin
// In koinDomainModule — the ONLY place implementations are wired to interfaces.
single<CategoryRepository> { CategoryRepositoryImpl(get()) }
```

This is what makes individual systems replaceable in 20 years: you swap the
implementation, never the interface. The day the database engine changes, exactly one file
changes per repository.

**Violation signal:** a `:feature:*` module that imports a class from `:data` or `:app`
directly instead of from `:core:domain` or `:domain`.

---

## Principle 3 — One Direction, One Source of Truth

State flows in exactly one direction:

```
database → repository → interactor → screen model → UI
```

State **never** flows backward. The UI never writes directly to the database. The database
never reads from the UI. There is exactly one place each piece of state is defined, and
everything else observes it.

A `ScreenModel` that calls `preferenceStore.get()` directly instead of going through a
domain interactor that owns that preference creates **two sources of truth** for the same
data. This is the root cause of the "why did my UI show stale data" bugs that are only
observable on real devices under real conditions — they are invisible in tests because
tests control timing.

**Rule:** `ScreenModel`s inject only domain **Interactors**, never repositories or
preference stores directly.

---

## Principle 4 — Explicit Failure, Never Silent Failure

Every function that can fail returns a typed error (`Result<T>`, sealed class, or throws
explicitly). Silent `catch (_: Exception) {}` blocks are architectural debt: they convert
hard failures into phantom bugs that are only observable in the real world, never in CI.

**Recoverable failures** (e.g., notification channel setup failed) should log at
`ERROR` and continue:

```kotlin
} catch (e: Exception) {
    logcat(LogPriority.ERROR, e) { "Failed to modify notification channels" }
}
```

**Unrecoverable failures** (e.g., Koin graph failed to start, database failed to open)
must **crash fast and loud**:

```kotlin
} catch (e: Exception) {
    StartupTracker.recordError(StartupTracker.Phase.KOIN_INITIALIZED, e)
    throw e  // crash visibly; CrashActivity will surface the stack trace
}
```

The `StartupTracker` and `StartupDiagnosticOverlay` exist precisely to surface
unrecoverable failures with full context on the device screen. Use `recordError()` at
every startup catch site before re-throwing.

---

## Principle 5 — Everything Observable Must Be Testable in Isolation

Each interactor must be testable by constructing it with fake repository implementations.
Each screen model must be testable by constructing it with fake interactors. Each
repository must be testable with an in-memory database substitute.

The architecture supports this because all repository and service contracts are interfaces.
The remaining work is ensuring that **every** service dependency is also an interface in
`domain`, not a concrete class that drags in Android dependencies:

| Class | Status | Target |
|---|---|---|
| `CategoryRepository` | ✅ Interface | — |
| `MangaRepository` | ✅ Interface | — |
| `DownloadManager` | ✅ Interface (`domain.download.service`) | — |
| `TrackerManager` | ✅ Interface (`domain.track.service`) | — |
| `NetworkHelper` | ⚠️ Concrete | Extract `NetworkClient` interface in domain |
| `ExtensionManager` | ⚠️ Concrete (aliased) | Formalise domain interface |

**Rule:** If a class cannot be instantiated in a JVM unit test with a fake collaborator,
it is not finished.

---

## Principle 6 — Startup Is a Contract, Not a Hope

The `StartupTracker` records every phase of app startup with wall-clock timestamps and
surfaces failures in the `StartupDiagnosticOverlay`. Every startup phase must be:

1. **Time-bounded** — if a phase takes more than N seconds, it is a bug, not a slow
   device.
2. **Failure-reporting** — `recordError()` must be called from every `catch` block during
   startup. Never log and swallow during startup.
3. **Causally ordered** — if Phase B depends on Phase A, that dependency is encoded in
   code (e.g., `Migrator.await()` blocks until `initGate` is completed), not assumed by
   convention.

The defined startup sequence and its correctness guarantees:

| Phase | Guarantee |
|---|---|
| `APP_CREATED` | `Application.onCreate()` entered |
| `KOIN_INITIALIZED` | Full DI graph is available; any failure crashes fast |
| `MIGRATOR_STARTED` | Version preference read; migrator coroutine running |
| `ACTIVITY_CREATED` | `MainActivity.onCreate()` entered |
| `COMPOSE_STARTED` | Compose content set; UI frame pipeline active |
| `MIGRATOR_COMPLETE` | All migrations done (or timed out); startup unblocked |
| `NAVIGATOR_CREATED` | Root navigator ready |
| `HOME_SCREEN_LOADED` | Content is visible and interactive |

If a phase never completes, the `StartupDiagnosticOverlay` shows it as pending
(⏳) on debug/nightly/preview builds. **A startup bug is not a "crash" — it may be a
hung phase that leaves the user on a blank screen.** The overlay makes this visible.

---

## Principle 7 — Dependency Injection Is Constructor Injection

Koin is the dependency injection framework. All dependencies are resolved at construction
time via constructor parameters — not by calling `get()` inside a method body, not by
`by inject()` as a lazy property outside the constructor scope, not by `Injekt.get()`.

The only permitted exception is the `uy.kohesive.injekt.Injekt` shim, which exists
exclusively to bridge legacy third-party extensions that cannot be recompiled. **Internal
code must never use `Injekt.get()`.**

**Enforced by:** the "Architecture fitness" step in `build.yml` which fails the CI build
on any `Injekt.get()` call outside the shim file.

The current `compileSafety.set(false)` in several module `build.gradle.kts` files is a
temporary compromise. The path to `compileSafety.set(true)` requires converting all
manual `module {}` blocks to `@Module` annotations (or declaring `@ExternalDefinitions`
when that annotation becomes available in Koin Annotations). This is the single highest
priority DI improvement remaining.

---

## Principle 8 — State Has Exactly One Representation at Each Layer

A preference value, a manga entity, a download state: each exists in exactly one
canonical form at each layer, and is **transformed** when crossing a boundary — not
duplicated.

- **Persistence layer** (Room entities, DataStore keys): raw storage types.
- **Domain layer** (Interactors, repositories): domain model types (`Manga`, `Chapter`).
- **Presentation layer** (ScreenModel state): immutable ViewState snapshots.
- **UI layer** (Composable): `@Stable` or `@Immutable` data passed as parameters.

Duplication of state between layers (e.g., a `MutableStateFlow` in a `ScreenModel` that
mirrors a database `Flow` without being derived from it) always diverges under concurrent
writes. **One flow. One source. One direction.**

---

## Canonical Anti-Patterns (Never Do These)

| Anti-pattern | Why it fails | Correct alternative |
|---|---|---|
| `Injekt.get()` in internal code | Service-locator pattern; graph not verified at compile time | Constructor injection via Koin |
| `ScreenModel` imports `*RepositoryImpl` | Bypasses domain interface boundary | Inject `*Repository` interface |
| `catch (_: Exception) {}` during startup | Swallows failures; invisible on device | `StartupTracker.recordError()` + re-throw |
| `runBlocking {}` on the main thread | Blocks UI; causes ANR | `Dispatchers.IO` + coroutine |
| `android.*` import in `:domain` | Prevents JVM-only testing; couples business logic to platform | Move Android-specific code to `:data`/`:app` |
| Unbounded `Channel<T>()` on an `object`-scoped screen | Accumulates undelivered events across navigation | `Channel.CONFLATED` or `Channel.DROP_OLDEST` |
| Multiple `MutableStateFlow`s in one `ScreenModel` | Multiple sources of truth; diverges under concurrent writes | Single immutable `ViewState` data class |
| `by inject()` field in a `ScreenModel` | Hidden dependency; untestable without Koin context | Constructor parameter |

---

## Guiding Question for Code Review

When reviewing a pull request, ask: **"Could I replace this system's implementation
without touching any of its callers?"**

If the answer is no — because callers import the concrete type, because the interface
leaks implementation details, because state flows in two directions — then the change is
not finished.

---

*This document supersedes any informal conventions previously used in the codebase. When
in doubt, read this document. When this document is wrong, update it and explain why in
the commit message.*
