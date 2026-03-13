# Ephyra Memory Management & Caching Analysis

## 1. PAGE LOADING AND CACHING

### PagerViewer (app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/)
- **File:** PagerViewer.kt:117
  - `offscreenPageLimit = 1` - Only keeps 1 page off-screen, efficient for memory
  
- **File:** PagerPageHolder.kt:75-110
  - Creates `MainScope()` for page loading
  - **Proper cleanup:** `scope.cancel()` called in `onDetachedFromWindow()` (line 109)
  - Jobs: `loadJob` and `smartCombineRetryJob` both properly cancelled

### WebtoonViewer (app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/)
- **File:** WebtoonViewer.kt:82-88
  - `setItemViewCacheSize()` configured based on device tier (LOW:2, MEDIUM:4, HIGH:8)
  - `MainScope()` created at line 39
  - **Proper cleanup:** `scope.cancel()` in `destroy()` (line 208)

- **File:** WebtoonPageHolder.kt:77-125
  - **MEMORY LEAK IDENTIFIED:** `MainScope()` created at line 77 but NOT cancelled in `recycle()`
  - Only `loadJob` is cancelled (line 118), scope itself persists
  - When views are recycled and reused, scope is never cleaned up
  - This creates a leak of the scope's Job and its context over time
  - `scope.cancel()` needs to be added to the `recycle()` method

### Page Holder Patterns
- **File:** PagerPageHolder.kt:192-270
  - Smart combine retry mechanism with `smartCombineRetryJob`
  - Page is loaded with both tiled decoding (source path) and bitmap transform (bitmap path)
  - Merged bitmap is cached in `page.mergedBitmap` to avoid re-merging

- **File:** WebtoonPageHolder.kt:134-200+
  - Same pattern as PagerPageHolder for loading
  - Missing scope cancellation in recycle()

## 2. BITMAP/IMAGE HANDLING AND RECYCLING

### ReaderPageImageView (app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ReaderPageImageView.kt)
- **File:** ReaderPageImageView.kt:182-190
  - `recycle()` method properly handles cleanup:
    - Disposes image request: `imageRequestDisposable?.dispose()`
    - Recycles SubsamplingScaleImageView: `it.recycle()`
    - Disposes AppCompatImageView (animated): `it.dispose()`
  - **Pattern is CORRECT**

### ReaderPage Model
- **File:** ReaderPage.kt:26-35
  - Stores `mergedBitmap: Bitmap?` for caching merged page results
  - `recycleMergedBitmap()` method properly calls `bitmap.recycle()`
  - Called when chapter is unreferenced

### ReaderChapter Lifecycle
- **File:** ReaderChapter.kt:29-48
  - Uses reference counting (`references` var at line 25)
  - `unref()` method (line 33):
    - Recycles merged bitmaps from all pages (lines 43-45)
    - Calls `pageLoader?.recycle()` (line 39)
    - Sets state back to Wait
  - **Cleanup is CORRECT**

### Image Rendering Pipeline (PagerPageHolder.kt:192-270)
- **Potential resource leak:** Lines 218, 223 - `.peek().inputStream()` calls
  ```kotlin
  BitmapFactory.decodeStream(source.peek().inputStream(), null, dimOpts)
  ImageUtil.chooseBackground(context, source.peek().inputStream())
  ```
  - These InputStream objects are created but inputStream() doesn't have explicit close
  - However, `peek()` returns a reference to the underlying buffer (not a copy)
  - InputStreams from peek() are closed implicitly after decodeStream/chooseBackground
  - **Pattern appears acceptable** since these are short-lived operations in try/catch block

## 3. IMAGE CACHING MECHANISMS

### ChapterCache (app/src/main/java/eu/kanade/tachiyomi/data/cache/ChapterCache.kt)
- **Backing:** Coil3's DiskCache with LRU eviction
- **Size config:** Based on device tier
  - LOW: 100 MB (line 37)
  - MEDIUM: 256 MB (line 40)
  - HIGH: 512 MB (line 43)
- **Good patterns:**
  - Line 94-99: `getPageListFromCache()` uses `.use {}` for snapshot cleanup
  - Line 171-188: `putImageToCache()` properly closes Response body in finally block (line 187)
  - Line 202-223: `fetchAndCacheImage()` properly closes response in finally (line 214)
  - Deduplication: `openEditor()` returns null if write in progress (line 206)
  
- **Snapshot management:** Line 159
  - `.use {}` properly closes snapshots to prevent leaks

### CoverCache (app/src/main/java/eu/kanade/tachiyomi/data/cache/CoverCache.kt)
- **Simple file-based cache:** Maps thumbnail URLs to hashed filenames
- **Stream handling:** Line 66-71
  - `setCustomCoverToCache()` uses `.use {}` for both input/output streams
  - **Proper cleanup**

### DownloadCache (app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadCache.kt)
- **Initialization:** Lines 102-123
  - Disk cache loaded with `.use {}` (line 108-109)
  - Protobuf deserialization properly handles stream
- **Architecture:** Maintains in-memory RootDirectory structure
  - Periodically renewed from filesystem (1 hour interval, line 84)
  - Mutex-protected for concurrent access
- **Invalidation:** Properly invalidated when storage changes

## 4. MEMORY LEAKS AND CLEANUP PATTERNS

### CRITICAL LEAKS IDENTIFIED

#### **LEAK #1: WebtoonPageHolder Scope Not Cancelled**
- **File:** WebtoonPageHolder.kt:77, 117-125
- **Issue:** `MainScope()` created but never cancelled
- **Impact:** Each recycled WebtoonPageHolder leaves a scope and associated coroutine infrastructure in memory
- **Severity:** HIGH - accumulates over page scrolling
- **Fix Required:**
  ```kotlin
  override fun recycle() {
      loadJob?.cancel()
      loadJob = null
      scope.cancel()  // <-- ADD THIS
      
      removeErrorLayout()
      frame.recycle()
      progressIndicator.setProgress(0)
      progressContainer.isVisible = true
  }
  ```

### CORRECT CLEANUP PATTERNS

#### PagerPageHolder
- **File:** PagerPageHolder.kt:103-110
  - `onDetachedFromWindow()` properly:
    - Cancels `loadJob` (line 105)
    - Cancels `smartCombineRetryJob` (line 107-108)
    - **Cancels scope** (line 109) ✓

#### ReaderActivity Lifecycle
- **File:** ReaderActivity.kt:332-344
  - `onDestroy()` calls `viewModel.state.value.viewer?.destroy()` (line 334)
  - Cancels toasts (lines 335-336)
  - **Proper cleanup** ✓

#### WebtoonViewer Lifecycle
- **File:** WebtoonViewer.kt:206-209
  - `destroy()` calls `scope.cancel()` ✓
  - **Proper cleanup**

## 5. DATABASE CURSOR/CONNECTION MANAGEMENT

### DatabaseHandler Pattern (Good)
- **File:** AndroidDatabaseHandler.kt:25-104
  - All database operations use SQLDelight's Query interface
  - **No raw cursors** - uses SQLDelight's high-level abstractions
  - Methods:
    - `awaitList()` - executeAsList() (line 33)
    - `awaitOne()` - executeAsOne() (line 40)
    - `awaitOneOrNull()` - executeAsOneOrNull() (line 54)
  - All executed within dispatcher context to prevent blocking main thread
  - **No manual cursor management needed** - SQLDelight handles it

### PagingSource Pattern (Good)
- **File:** QueryPagingSource.kt:8-71
  - Implements Android Paging3 library
  - **Good practices:**
    - Line 16: `currentQuery` observed with `.observable()` to auto-deregister listeners
    - Line 22-25: Unregisters query listener on invalidation
    - Line 39-42: Sets current query to trigger listener registration
    - No manual cursor management
  - **Proper cleanup**

### Repository Implementations
- **Pattern:** All use `handler.await()` or `handler.awaitList()`
- **File examples:** ChapterRepositoryImpl.kt, MangaRepositoryImpl.kt
  - Operations are wrapped in transactions when needed
  - Database access is properly dispatched to IO dispatcher
  - **No resource leaks from database access**

### Transaction Management
- **File:** AndroidDatabaseHandler.kt:89-103
  - Checks if already in transaction via `driver.currentTransaction()`
  - Uses withContext to dispatch to appropriate dispatcher
  - **Proper transaction handling**

## SUMMARY OF ISSUES

### Critical Issues (Fix Required)
1. **WebtoonPageHolder scope leak** - Scope created but not cancelled on recycle
   - Location: WebtoonPageHolder.kt:77, 117-125
   - Impact: Memory leak accumulates as user scrolls
   - Fix: Add `scope.cancel()` to recycle() method

### Warnings (Monitor)
1. **InputStreams from peek()** - Line 218, 223 in PagerPageHolder.kt
   - These appear safe due to short-lived nature
   - Consider wrapping in try-with-resources for clarity

### Good Patterns (No Issues)
1. PagerPageHolder scope properly cancelled in onDetachedFromWindow()
2. ChapterCache and CoverCache use .use {} for resource cleanup
3. ReaderChapter reference counting with bitmap recycling
4. Database operations through DatabaseHandler abstraction
5. No raw Cursor usage - all via SQLDelight
6. Config size caching based on device performance tier

## RECOMMENDATIONS

1. **Immediate:** Add `scope.cancel()` to WebtoonPageHolder.recycle()
2. **Code clarity:** Wrap `.peek().inputStream()` operations in try-with-resources
3. **Monitoring:** Consider adding leak detection to debug builds
4. **Testing:** Add lifecycle tests for viewers to catch scope leaks early
5. **Documentation:** Mark ReaderPage lifecycle methods as critical (recycleMergedBitmap, etc.)
