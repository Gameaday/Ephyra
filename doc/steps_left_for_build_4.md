# Steps Left for Build 4 — [UI & Feature Parity: Two-Handed Gestures]

This document details the engineering specifications and architectural integration paths for implementing robust two-handed gesture controls under **Phase 6 (UI & Feature Parity)** of Ephyra's roadmap.

---

## 🏛️ Phase 6: Accomplished Foundations

- [x] **Compose Preferences Core**: Built a native Compose settings system (`PreferenceScreen.kt`, `PreferenceItem.kt`) styled with pure, beautiful Material 3 tokens.
- [x] **Material 3 Dynamic Preferences**: Implemented context-wrapped theme resolution (`SourcePreferencesScreen.kt`) allowing legacy `PreferenceFragmentCompat` structures to adapt their styles, text colors, and spacings dynamically to the app's standard Material 3 styles when embedded inside Compose scaffolds.

---

## 🚀 Active Actions: Two-Handed Gestures Implementation

The next targets involve implementing elegant, responsive gestures inside our media viewing and library management modules.

### Step 4.1: Library Two-Handed Gesture Controls
- [x] **Scale/Pinch-to-Grid**:
  - Integrated a multi-touch `Modifier.pointerInput` listener in [LazyLibraryGrid.kt](file:///c:/Project/Android/Ephyra/feature/library/src/main/kotlin/ephyra/feature/library/presentation/components/LazyLibraryGrid.kt).
  - Detects pinch gestures dynamically to scale card sizes and change the column count with seamless preference updates.
- [x] **Two-Finger Quick Filter Swipe**:
  - Implemented horizontal two-finger swipe detection in [LibraryContent.kt](file:///c:/Project/Android/Ephyra/feature/library/src/main/kotlin/ephyra/feature/library/presentation/components/LibraryContent.kt) to rapidly toggle the settings sheet/category drawers.

### Step 4.2: Reader Two-Handed Navigation Gestures
- [x] **Swipe-from-Bottom for Next Page**:
  - Integrated a swift upward flick gesture detector in the root reader layout in [ReaderActivity.kt](file:///C:/Project/Android/Ephyra/feature/reader/src/main/kotlin/ephyra/feature/reader/ReaderActivity.kt) tracking vertical velocity.
  - Upward flicks starting in the bottom 25% zone trigger page transition (`moveToNext()`) in standard Pager and Webtoon viewers, enabling comfortable reachability.

