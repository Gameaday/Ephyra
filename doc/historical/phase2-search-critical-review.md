# Phase 2.1 — Critical Review & Staged Rollout

> **Status:** HISTORICAL - completed improvement-session record. Retained for archaeology only;
> not current guidance. Current forward plan: [`../2_0_COMPLETION_PLAN.md`](../2_0_COMPLETION_PLAN.md).

Owner constraints folded in: no quality polling during search/browse (compute lazily on
series selection, cache thereafter); shift slowly; one search front door is
non-negotiable; poor matching is the feared failure mode.

## The nagging thought, made precise

**Auto-pairing can corrupt, not just duplicate.** Duplicates are visible and harmless;
a *wrong* authority pairing silently attaches the wrong canonical id, wrong metadata,
and (once tracking/progress syncs exist) wrong progress associations to a library
entry. That is strictly worse than the disease.

Three compounding gaps found in review:

1. **The canonical key can collide across different works.**
   `canonicalKey(title, author, genres)` has no year or content type. Remakes,
   one-shot vs serialization, same-title different franchise ("Hunter × Hunter"
   1999/2011 pattern) normalize to the same key. Silent pairing (D3) must therefore
   require *more* than the hash: external/canonical id, or hash + agreeing
   year/type/format. The hash alone stays a candidate-generator, never a merge-proof.
2. **Narrowing the merge prompt removes a safety feature.** Keeping it only for fuzzy
   matches (D3) is right, but "exact hash" is weaker evidence than it looked. Rule:
   silent only on external-id match; hash+attribute agreement gets a lightweight
   inline confirm (not a modal gauntlet — one tap on the row action, undoable).
3. **Every silent add needs an undo.** Not a prompt — a snackbar undo. Prompts
   before, or undo after; never neither.

## Things possibly missing

- **Bootstrap dead-end (the obvious one):** availability-first (D11) + fresh install
  with no sources + empty library = a search that always shows nothing and looks
  broken. Needs: guided empty state ("add a source"), and the "include unavailable"
  toggle auto-enabled while no sources are installed.
- **Availability badges are fuzzy too.** "On N sources" is derived from the fan-out's
  title matches — it can be wrong per row. It must stay advisory at search time; the
  real binding is resolved (and confirmed if uncertain) only at add/open time. Never
  let the badge's count become load-bearing for silent behavior.
- **Quality scores need invalidation.** Cached quality goes stale as sources update.
  TTL + invalidate on library refresh/update checks; otherwise a source that was best
  in March stays "best" forever.
- **Re-preference flapping.** If quality scoring re-picks the binding on every open,
  a user's series can hop sources back and forth as scores jitter. Only re-prefer when
  the challenger beats the incumbent by a margin, and never silently switch a binding
  the user picked manually (D2 picker choice is sticky).

## Owner's laziness constraint adopted (and extended)

- No quality/availability verification calls during search or browse scroll. Search
  uses only what the fan-out already returned.
- Quality scoring triggers on series open/add, for that series' candidate sources
  only; results cached with TTL.
- Add-time binding choice without quality data: attach the first usable binding
  (cheapest known), mark it unscored; first open rescores and upgrades if the margin
  rule says so.

## Staged rollout (each step independently shippable & reversible)

- **Stage A — one front door, zero behavior risk.** Single search screen composing
  existing engines as sections (Library / Authority / Sources). Both old destinations
  redirect here. No new matching logic at all. This delivers the non-negotiable (kill
  the two-door problem) without touching pairing.
- **Stage B — availability-first.** Filter + "include unavailable" toggle + bootstrap
  empty states. Pure presentation over Stage A data.
- **Stage C — paired add with undo.** Silent only on external-id; inline confirm for
  hash+attributes; snackbar undo everywhere; authority→source and source→authority
  completion both live.
- **Stage D — lazy quality scoring + caching + stable re-preference** (margin rule,
  manual-choice stickiness, TTL invalidation).
- **Stage E — removals.** `MatchResultsScreen`, the post-add source prompt, tracker
  picker UI. Only after C/D prove out.

## Recommendation

Start Stage A now. It is the lowest-risk highest-value step and is mostly composition
work. C is the stage that deserves the most test coverage (it is where corruption can
happen); D is where the caching lifecycle work (Phase 6) intersects.

## Revision: authority removed from user-facing search (owner decision, D13)

The staged rollout simplifies:

- **Stage A — one front door** composing Library + Sources sections only. The
  authority screen is removed as a destination (code preserved). Sources section
  groups by `canonicalKey` (exact) with fuzzy as secondary — weaker dedup than
  authority-backed rows, accepted for v1.
- **Stage B** unchanged (availability-first).
- **Stage C — pairing** is no longer a search feature; it becomes the background
  enrichment pipeline (silent external-id attaches; review queue for the rest).
  Undo surface lives in details (unlink).
- **Stage D** unchanged (lazy quality scoring, caching, stable re-preference).
- **Stage E — removals** grows: tracker picker, merge-prompt gauntlet,
  MatchResultsScreen, post-add source prompt, *and* the AuthoritySearch screen/event
  classes once enrichment absorbs their logic.

Risks C carried move off the discovery path entirely; enrichment matching still needs
the confidence rules (silent = external-id only), but a wrong attach is now a visible,
unlinkable detail on one library entry rather than a broken search result.
