# Ephyra Cache Policy

This document defines the ownership and lifecycle of image data. The goal is not to maximize cache hits; it is to avoid unnecessary network and decode work while preventing stale, corrupt, duplicated, or unbounded data.

## Principles

1. A cache has one durable source of truth.
2. Durable source bytes are separated from disposable decoded images.
3. Every durable entry has a stable identity and an invalidation rule.
4. Cache failures fall back to the original source; they never become permanent failures.
5. Writes are atomic where a partially written file could be mistaken for valid data.
6. Cache cleanup protects user-owned and library-critical data.
7. Cache size and age are bounded deliberately, not by accident.
8. Cache hits and misses are observable.

## Asset matrix

| Asset | Durable store | Decoded/working store | Identity | Invalidation | Retention |
|---|---|---|---|---|---|
| Remote cover | `core:data` `CoverCache` | Coil memory cache | Canonical thumbnail URL plus cover revision | URL change, `coverLastModified`, explicit cache deletion | Age/size bounded; library covers protected |
| Custom cover | `CoverCache` custom directory | Coil memory cache | Manga ID plus cover revision | Replace or delete custom cover | Until user removes it or clears app data |
| Downloaded page bytes | Chapter download/cache store | `ReaderPage.cachedBytes` and Coil memory | Chapter, page, source revision | Download replacement/removal | Chapter/cache policy |
| Online page bytes | Chapter cache | `ReaderPage.cachedBytes` and Coil memory | Chapter, page, image URL/revision | Cache eviction or source change | Bounded by chapter/cache policy |
| Merged page | No independent durable store | `ReaderPage.mergedBitmap` | Parent page identity | Parent page change or retry | Disposable |
| Webtoon slice | No independent durable store | Slice bitmaps in composition | Page bytes plus slice geometry | Page change/disposal | Disposable |

## Cover flow

```text
request
  -> custom cover file, if present
  -> dedicated remote cover file, if present
  -> legacy Coil disk snapshot migration, if present
  -> network fetch
  -> atomic write to dedicated remote cover file
  -> Coil decode/memory cache
```

The dedicated remote cover store is authoritative. A remote cover must not be independently persisted as a second durable copy in Coil disk cache. Coil disk snapshots are read only for migration compatibility.

## Page flow

```text
page loader
  -> durable chapter bytes when available
  -> bounded ReaderPage working bytes
  -> Coil decoded-page cache
  -> pager image or webtoon slices
```

Page and cover identities are intentionally separate. They have different dimensions, lifetimes, invalidation rules, and retention policies.

## Cleanup rules

- Custom covers are never removed by ordinary remote-cover pruning.
- Library remote covers are protected while their library metadata references them.
- Browse/search remote covers are pruned first when stale.
- Cleanup must be bounded by both age and, where measurements justify it, total size.
- A cache miss is always safe: the loader may fetch the source again.
- A corrupt durable entry must be deleted and treated as a miss.

## Verification requirements

Before changing retention constants, measure:

- Persistent cover hit rate and network fetches.
- Cover cache bytes and prune counts.
- `ReaderPage.cachedBytes` retained by long chapters.
- Decoded bitmap memory under pressure.
- Retry and recovery behavior.
- Cache behavior after process death, app restart, and app data clear.
