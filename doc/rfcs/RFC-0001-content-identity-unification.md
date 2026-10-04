# RFC-0001: Content Identity Unification

Status: Draft (brainstorm)
Context: Improvement session Phase 2.1 (unified search) / Phase 3 (Jellyfin)

## Problem

Content identity is currently *source-shaped*, not *work-shaped*:

- A `Manga` row's identity is `(source, url)` — the same book from two sources is two
  unrelated rows, each with its own chapters, history, tracking, and cover cache entry.
- A parallel model (`ContentItem`) exists with lossy two-way mappers
  (`ContentMappers`), so "what is this thing" has two answers depending on which layer
  you ask.
- `CanonicalDeduplicator` merges only **in memory during ingest**; the canonical hash
  lives in a `metadata` map string, never as a first-class persisted key. Nothing
  downstream (library, updates, history, search) can join on it.
- `UnifiedContentSource`, `ContentSourceEngine`, `ContentAdapter`, `RemoteSource`, and
  the legacy extension `Source` are five overlapping "what is a source" concepts, with
  authority split unclearly (orchestrator registry vs profile cache vs extension APKs).
- Consequences: orphaned rows when a source disappears or an item's URL changes,
  duplicate covers in cache (Phase 4 symptom), search that returns the same work N
  times, migration feature having to re-match what identity should have made trivial.

## Proposal: Work / Binding split

Two levels of identity, explicitly:

**Work** — the discrete thing the user means. One row.
- `workId` (stable app-generated id)
- canonical key: normalized title + author (+ type), i.e. promote
  `CanonicalDeduplicator.generateContentHash` to a real indexed column
- merged display metadata (title, cover pref order, description, genres)
- user state lives here: favorite, categories, read progress summary, tracking links

**Binding** — "provider P offers Work W at address A". Many per work.
- `bindingId`, `workId → Work`, `providerId`, `ContentLocator`
- provider-specific metadata payload (raw title, chapter/unit list cache, health)
- priority/preference per binding (user picks preferred edition/provider; auto =
  healthiest)

Sources/providers reduce to **one concept**: a provider is anything implementing one
interface (catalog, search, units, pages). Extension APK, Jellyfin, OPDS, local
archives all become providers; the engine/profile/orchestrator collapse becomes a
registry of provider factories keyed by provider type. `SourceProfile` becomes provider
config (endpoint + credentials + type), not an identity carrier.

## What this fixes

- **Unified search**: search fans out to providers, results resolve to Works
  (create-or-match by canonical key), UI shows one row per work with "available at N
  providers" — matching the brainstorm goal: search discrete things, see which
  providers have them.
- **Orphans**: removing a provider deletes bindings; works survive. A binding whose
  address dies is re-resolvable by matching the work against the provider again.
- **Cover cache (Phase 4)**: one cache slot keyed by `workId` — the exact "same cover,
  one spot" requirement falls out of identity instead of being patched per screen.
- **Migration feature**: becomes "re-point bindings", not fuzzy re-identification.
- **Jellyfin (Phase 3)**: just a new provider; its items bind to existing works when
  the user already has them locally or from extensions.

## Matching rules (the hard part — needs decision)

1. Exact canonical hash match (normalized title+author) → same work, auto-bind.
2. Strong external id (ISBN, AniList/MAL id, Jellyfin provider ids) → auto-bind,
   overrides hash.
3. Title-only fuzzy match → candidate, confirmed on user action ("add to library" picks
   existing work) — never silently merged.
4. Title normalization (`TitleNormalizer`, `MediaMatcher`) must be unified into one
   normalizer used by ingest, search, and binding — today there are three.

## Migration path (phased, no big bang)

1. Add `work` + `binding` tables; backfill works from existing rows (group by canonical
   hash); each existing `manga` row becomes work + one binding. Keep old table as view
   of the join until callers migrate.
2. Move cover cache key to workId (Phase 4 consumes immediately).
3. Unified search resolves through works (Phase 2.1).
4. Jellyfin provider writes bindings (Phase 3).
5. Retire `ContentItem` mappers and `metadata["canonical_hash"]`.

## Open questions

- Do we expose multiple bindings in UI (editions/providers picker) or keep it
  invisible auto-preference? (Recommend: invisible by default, picker in details.)
- Read progress: per-work or per-binding chapter lists when providers number chapters
  differently? (Recommend: progress on work-unit index with per-binding mapping table.)
- Who owns canonical metadata when providers disagree — first-seen, user-preferred
  provider, or manual edit? (Recommend: user-preferred provider, else richest.)
- Merge/split UI for mistakes — needed at v1 or deferrable?
