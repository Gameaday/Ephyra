# Reader Module P0 Fix: Error Handling Consistency

**PR Title:** fix(reader): consistent error handling in chapter loading

**Related Issues:** R-A-001, R-A-002, R-A-003

---

## Summary

This PR fixes inconsistent error handling in the chapter loading pipeline:

1. **ChapterLoader** no longer re-throws exceptions after capturing them in chapter state
2. **ReaderViewModel.loadChapterPages** properly catches exceptions and returns false on failure
3. **ChapterLoadingCoordinator** handles both open and preload paths consistently

---

## Changes

### 1. ChapterLoader.kt

**Before:**
```kotlin
try {
    // ... load pages ...
    chapter.state = ReaderChapter.State.Loaded(pages)
} catch (e: Throwable) {
    chapter.state = ReaderChapter.State.Error(e)
    throw e  // REMOVED: don't re-throw
}
```

**After:**
```kotlin
try {
    // ... load pages ...
    chapter.state = ReaderChapter.State.Loaded(pages)
} catch (e: Throwable) {
    chapter.state = ReaderChapter.State.Error(e)
    // Don't re-throw: error is captured in state
}
```

**Rationale:** The error is already captured in `chapter.state`. Re-throwing creates inconsistent behavior where callers may see both an exception AND an error state. Callers should check the chapter state, not catch exceptions.

---

### 2. ReaderViewModel.kt

**Before:**
```kotlin
override suspend fun loadChapterPages(chapter: ReaderChapter, isPreloadOnly: Boolean): Boolean {
    val loader = loader ?: return false
    loader.loadChapter(chapter, isPreloadOnly = isPreloadOnly)  // Can throw!
    return true  // Always returns true, even on failure
}
```

**After:**
```kotlin
override suspend fun loadChapterPages(chapter: ReaderChapter, isPreloadOnly: Boolean): Boolean {
    val loader = loader ?: return false
    return try {
        loader.loadChapter(chapter, isPreloadOnly = isPreloadOnly)
        true
    } catch (e: Throwable) {
        if (e is CancellationException) throw e  // Let cancellation propagate
        false  // Return false for all other errors
    }
}
```

**Rationale:** The method's contract (from ChapterLoadingEffectHandler) implies it should return `false` when loading fails. Currently it always returns `true` (except when loader is null), even when an exception is thrown. This misleads callers.

**CancellationException Handling:** Cancellation is a control flow mechanism, not an error. We still throw it so coroutines can be properly cancelled.

---

### 3. ChapterLoadingCoordinator.kt

**Before:**
```kotlin
// Preload path:
val loaded = try {
    effects.loadChapterPages(chapter, isPreloadOnly = true)
} catch (e: Throwable) {
    if (e is CancellationException) {
        throw e
    }
    return  // Silently swallows
}

// Open path:
if (!effects.loadChapterPages(chapter, isPreloadOnly = false)) return
// Does NOT catch exceptions
```

**After:**
```kotlin
// Preload path:
val loaded = try {
    effects.loadChapterPages(chapter, isPreloadOnly = true)
} catch (e: Throwable) {
    if (e is CancellationException) {
        throw e
    }
    return  // Still swallows (but now consistent with return value from loadChapterPages)
}

// Open path:
if (!effects.loadChapterPages(chapter, isPreloadOnly = false)) return
// Now consistent: loadChapterPages returns false on error
```

**Rationale:** With the fix to ReaderViewModel.loadChapterPages, both paths now work consistently:
- On success: returns `true`
- On failure: returns `false` (except for CancellationException)
- The coordinator checks the return value and handles accordingly

The preload path's silent return is acceptable because:
1. The error is captured in chapter.state
2. The chapter won't be loaded, so user sees the error state
3. Preload failures are non-critical (we tried, it failed, move on)

---

## Testing

### Existing Tests
All existing tests should pass. The changes are behavioral improvements that maintain the same external behavior:
- Chapter state is still set to Error on failure
- Preload failures are still silently ignored (by design)
- Open failures now return false instead of throwing

### New Tests Needed
1. Test that ChapterLoader doesn't throw on error
2. Test that ReaderViewModel.loadChapterPages returns false on error
3. Test that ChapterLoadingCoordinator handles preload failures gracefully
4. Test that ChapterLoadingCoordinator handles open failures gracefully

---

## Risk Assessment

**Risk Level:** LOW

**Rationale:**
- Changes are localized to the chapter loading pipeline
- Error states are still properly captured
- The only behavioral change is that exceptions no longer propagate from the open path
- This is actually MORE robust, not less

**Rollback Plan:**
If issues are found, revert the entire PR. The changes are co-dependent.

---

## Verification Checklist

- [ ] All existing tests pass
- [ ] New tests added for error handling
- [ ] Manual testing: load chapter that fails (network error, corrupted archive)
- [ ] Manual testing: preload chapter that fails
- [ ] Manual testing: rapid chapter switching
- [ ] Manual testing: cancellation behavior

---

## Related Code

- `ChapterLoader.kt` - Main loader
- `ReaderViewModel.kt` - ViewModel that uses loader
- `ChapterLoadingCoordinator.kt` - Orchestrates loading
- `ChapterLoadingEffectHandler.kt` - Interface

---

## Files Changed

1. `feature/reader/src/main/kotlin/ephyra/feature/reader/loader/ChapterLoader.kt`
2. `feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderViewModel.kt`
3. `feature/reader/src/main/kotlin/ephyra/feature/reader/loading/ChapterLoadingCoordinator.kt`
