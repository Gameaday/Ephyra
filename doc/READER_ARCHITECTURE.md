# Reader Architecture Contract

> **Status:** binding technical contract. This document defines the replacement reader; it does not authorize more patches to the legacy reader.

## Ownership

```text
ReaderSession        owns workflow state
ReaderResourceOwner  owns chapter/page working resources
Viewport              owns transient transform and pointer state
UI                    renders immutable snapshots and sends commands
```

`ReaderActivity` may host the immersive surface, but it does not own reader policy. Viewers do not own chapter navigation. `ReaderPage` does not own durable bytes or decoded render artifacts.

## State

```kotlin
ReaderSessionState(
    sessionId,
    chapterId,
    pageId,
    pageIndex,
    direction,
    viewportMode,
    transform,
    menuVisible,
    loading,
    error,
)
```

Valid states are explicit. A reader cannot be simultaneously `loading`, `ready`, and `error`. A page identity cannot change while a transition command is pending without a cancellation/reset result.

## Commands

```text
OpenChapter
CloseChapter
SelectPage
MoveForward
MoveBackward
BeginTransform
UpdateTransform
CommitTransform
CancelTransform
DoubleTap
ToggleMenu
RetryPage
RestoreState
```

Commands are semantic. Pointer events, Coil results, and database callbacks become commands or results; they do not mutate UI state directly.

## Effects

```text
LoadChapter
LoadPage
Prefetch
PersistProgress
NavigateChapter
PersistViewport
ClearWorkingResources
ShowError
```

Every effect is cancellable and has an owner. Duplicate delivery must be idempotent or explicitly rejected.

## Resource rules

- Durable page bytes belong to the chapter/download owner.
- Working page bytes are bounded by a documented byte budget.
- Decoded images belong to the viewport/memory owner.
- Merged/sliced/tile artifacts are disposable.
- Chapter progress is persisted before releasing the chapter.
- Retry invalidates the failed page's working resources and derived render identity.
- Page and chapter identity survives recomposition and process recreation.

## Chapter navigation

The canonical ordered chapter list is the navigation universe. `skipRead` may filter only forward candidates. Backward navigation must retain read chapters unless the user explicitly chooses a stricter filter. A missing canonical neighbor is different from a filtered neighbor.

## Replacement boundary

The legacy `ReaderViewModel`, current viewer state, current page resource model, and old transition UI are migration inputs only. They are deleted after the replacement passes the evidence gate; they are not maintained as parallel implementations.

## Evidence

- reducer table for every command;
- state-owner diagram;
- chapter navigation matrix;
- retry/resource invalidation tests;
- rotation/process recreation tests;
- device viewport and transition evidence.