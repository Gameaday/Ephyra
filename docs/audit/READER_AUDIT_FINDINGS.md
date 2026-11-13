# Reader Module Audit - Findings

**Audit Date:** 2026-11-13  
**Status:** In Progress  
**Phase:** A (Health & Design Audit)

---

## Executive Summary

This document contains findings from the systematic audit of the reader module. Issues are categorized by severity and include:
- **High Priority (P0):** Must fix - crashes, data loss, silent failures
- **Medium Priority (P1):** Should fix - inconsistency, poor UX
- **Low Priority (P2):** Nice to fix - minor improvements

---

## P0 - Critical Issues (Must Fix)

### R-A-001: ChapterLoader re-throws exceptions after capturing in state

**Location:** `feature/reader/src/main/kotlin/ephyra/feature/reader/loader/ChapterLoader.kt` lines 117-122

**Issue:**
```kotlin
try {
    // ... load pages ...
    chapter.state = ReaderChapter.State.Loaded(pages)
} catch (e: Throwable) {
    chapter.state = ReaderChapter.State.Error(e)  // Error captured here
    throw e  // But then re-thrown!
}
```

The exception is captured in the chapter state, but then re-thrown. This creates inconsistent error handling:
- The chapter state indicates error
- But the exception propagates up, potentially crashing the caller

**Impact:**
- Inconsistent error handling between open and preload paths
- Potential crashes in callers that don't expect exceptions
- Confusing state: chapter shows error but exception also propagates

**Evidence:**
- ChapterLoadingCoordinator.preloadChapter() catches exceptions (line 92-96)
- ChapterLoadingCoordinator.openChapter() does NOT catch exceptions (line 66)
- ReaderViewModel.loadChapterPages() does NOT catch exceptions

**Fix:**
Remove the `throw e` line. The error is already captured in the state, and callers should check the state rather than catching exceptions.

**Priority:** P0 - Critical

---

### R-A-002: ReaderViewModel.loadChapterPages always returns true

**Location:** `feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt` lines 551-554

**Issue:**
```kotlin
override suspend fun loadChapterPages(chapter: ReaderChapter, isPreloadOnly: Boolean): Boolean {
    val loader = loader ?: return false
    loader.loadChapter(chapter, isPreloadOnly = isPreloadOnly)  // Can throw!
    return true  // Always returns true, even if loadChapter threw
}
```

The method always returns `true` (except when loader is null), even if `loadChapter()` throws an exception. The exception will propagate up, but the return value is misleading.

**Impact:**
- ChapterLoadingCoordinator expects `false` when loading fails
- The contract of the interface is violated
- Callers cannot distinguish between success and failure by return value

**Evidence:**
- ChapterLoadingEffectHandler.loadChapterPages() returns Boolean
- Documentation says: "Returns false when no loader exists"
- But it should also return false when loading fails

**Fix:**
Wrap the loadChapter call in try-catch and return false on exception:
```kotlin
override suspend fun loadChapterPages(chapter: ReaderChapter, isPreloadOnly: Boolean): Boolean {
    val loader = loader ?: return false
    return try {
        loader.loadChapter(chapter, isPreloadOnly = isPreloadOnly)
        true
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        false
    }
}
```

**Priority:** P0 - Critical

---

### R-A-003: Inconsistent error handling between open and preload paths

**Location:** 
- `feature/reader/src/main/kotlin/ephyra/feature/reader/loading/ChapterLoadingCoordinator.kt` lines 92-96
- `feature/reader/src/main/kotlin/ephyra/feature/reader/loading/ChapterLoadingCoordinator.kt` line 66

**Issue:**
```kotlin
// Preload path (line 92-96):
val loaded = try {
    effects.loadChapterPages(chapter, isPreloadOnly = true)
} catch (e: Throwable) {
    if (e is CancellationException) {
        throw e
    }
    return  // Silently swallows non-cancellation exceptions
}

// Open path (line 66):
if (!effects.loadChapterPages(chapter, isPreloadOnly = false)) return
// Does NOT catch exceptions - they propagate up
```

The preload path silently swallows exceptions (except CancellationException), while the open path lets them propagate. This inconsistency means:
- Preload failures are silently ignored
- Open failures can crash the app

**Impact:**
- Preload failures are invisible to users
- Open failures may crash the reader
- Inconsistent behavior

**Fix:**
Both paths should handle errors consistently. Either:
1. Both catch and handle gracefully, OR
2. Both let exceptions propagate

Recommended: Both should handle gracefully since the error state is already captured in the chapter.

**Priority:** P0 - Critical

---

## P1 - High Priority Issues (Should Fix)

### R-A-004: ArchivePageLoader type mismatch in stream assignment

**Location:** `feature/reader/src/main/kotlin/ephyra/feature/reader/loader/ArchivePageLoader.kt`

**Issue:** 
Based on the previously reverted dubious changes, there appears to be a type mismatch where `resource.stream` expects a lambda `() -> InputStream?` but the code assigns `InputStream?` directly.

**Impact:**
- Compilation error
- Type safety violation

**Fix:**
Ensure stream is assigned as a lambda: `resource.stream = { reader.getInputStream(entry.name) }`

**Priority:** P1 - High

---

## P2 - Medium Priority Issues (Nice to Fix)

### R-A-005: Missing null checks in various loaders

**Location:** Multiple page loader implementations

**Issue:**
Various loaders may not properly handle null/missing resources.

**Impact:**
- Potential NPEs
- Silent failures

**Fix:**
Add proper null checks and error handling.

**Priority:** P2 - Medium

---

## Audit Progress

### Completed
- [x] R-A-001: ChapterLoader exception handling
- [x] R-A-002: ReaderViewModel.loadChapterPages return value
- [x] R-A-003: Inconsistent error handling in coordinator
- [ ] R-A-004: ArchivePageLoader type issues (needs investigation)
- [ ] R-A-005: Null checks in loaders (needs investigation)

### In Progress
- Error handling gaps in loaders
- Unhandled edge cases

### Pending
- Dead code paths
- Performance issues
- Design consistency
- Safety concerns

---

## Next Steps

1. **Fix P0 issues immediately** - These are critical and must be addressed
2. **Verify fixes with tests** - Ensure no regressions
3. **Continue with remaining audit categories**
4. **Document all findings**
5. **Create fix PRs**

---

## Commit Strategy

Each fix will be committed as a separate, focused PR:
- PR #1: Fix R-A-001, R-A-002, R-A-003 (error handling consistency)
- PR #2: Fix R-A-004 (type safety)
- PR #3: Fix R-A-005 and other issues

All PRs will follow existing gates:
- Four green checks
- Red rounds disclosed
- Documentation separate
