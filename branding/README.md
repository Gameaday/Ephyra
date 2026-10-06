# Ephyra — Brand Assets

Vector identity for **Ephyra 2.0**, the manga / comic / light-novel reader.

| File | What it is | ViewBox |
|------|------------|---------|
| `ephyra-mark.svg` | Square app mark / monogram (tile + page-panel “E” + coral wave) | `0 0 512 512` |
| `ephyra-logo.svg` | Primary horizontal lockup — mark + “Ephyra” wordmark | `0 0 256 64` |
| `ephyra-adaptive-icon-foreground.svg` | Android adaptive-icon foreground layer | `0 0 108 108` |

All files are standalone SVG: pure vector, no external fonts, no raster content,
no JavaScript, no `@import`, no external `href`.

---

## Palette

| Role | Name | Hex | Usage |
|------|------|-----|-------|
| Primary | **Abyss Teal** | `#0E4C5C` | Tile, wordmark, single-colour mark on light |
| Accent | **Ember Coral** | `#FF7A59` | The wave / sea gesture only |
| Neutral | **Sea Paper** | `#F5F1E8` | Monogram, text on teal, single-colour mark on dark |

Three colours, one job each. Keep the coral reserved for the wave so it stays a
deliberate accent rather than decoration.

**Contrast**
- Sea Paper on Abyss Teal ≈ **8.4:1** — passes WCAG AAA for all text sizes.
- Ember Coral on Abyss Teal ≈ **3.7:1** — fine for the large wave shape, not for text.

> Note: the in-app **Ephyra** Compose theme (`THEME_STYLE_GUIDE.md`) currently uses
> Electric Indigo `#4F46E5` + Cyan `#0891B2`. This mark is an intentional 2.0 refresh;
> migrate the theme tokens to the palette above when convenient so app and mark match.

---

## Clear space

Use **X = one page-panel width** (the thickness of the “E” strokes) as the spacing unit.

- **Mark** — keep a margin of **2X** clear on all four sides
  (≈ 104 units on the 512 grid, ≈ 10 px around a 48 px icon).
- **Horizontal logo** — keep a margin equal to the **cap height of the wordmark**
  (32 units on the 64-high logo) on all sides.
- Never place other elements, edges, or competing colour inside the clear space.

## Minimum sizes

| Asset | Minimum | Preferred |
|-------|---------|-----------|
| `ephyra-mark.svg` | **24 px** (digital), 32 px favicon | 48 px+ |
| `ephyra-logo.svg` | **120 px** wide | 160 px+ |

Below 120 px the wordmark is hard to read — switch to the **mark alone**.

## Monochrome

The mark is designed to survive as a **single colour**.

- **On light backgrounds** — render the monogram (page-panel “E” + wave) in
  Abyss Teal `#0E4C5C` on a transparent or paper field.
- **On dark backgrounds** — render the monogram in Sea Paper `#F5F1E8`
  (or pure white) on a transparent field; the coral wave becomes the same colour.
- **One-colour tile** — Abyss Teal tile `#0E4C5C` + Sea Paper monogram
  (this is exactly the default mark, with the coral wave re-coloured to paper).
- Use the monogram paths with a single `fill`; the wave may simply be dropped if a
  solid, compact mark is needed.

## Android adaptive icon

`ephyra-adaptive-icon-foreground.svg` is the **foreground** layer only.

- **Background layer:** solid Abyss Teal `#0E4C5C`.
- The monogram is centred on `(54, 54)` and kept inside the central **66 dp safe
  zone** (≈ 30 dp radius). The coral wave is decorative and bleeds to the edge by
  design, so it is not clipped by round/squircle masks.
- Result composes to the same image as `ephyra-mark.svg`.

## Colour-on-background cheat sheet

| Surface | Treatment |
|---------|-----------|
| Light / white | Full-colour mark, or monogram in Abyss Teal |
| Dark / near-black | Monogram in Sea Paper (coral wave → paper) |
| Photography / busy art | Full-colour mark on its own tile, or paper monogram in a solid teal tile |

---

## Design rationale

Ephyra reads as a **bold geometric “E” built from three stacked page panels** —
sequential art, a fanned stack of pages, a reader — standing on a single
**coral wave** that gives the sea-nymph her water and keeps the mark from feeling
like a generic tech monogram. The soft, generous corner radii mirror the app’s
own “Ephyra” theme shape language, so the icon and the UI feel cut from the same
cloth, while the deep-teal-plus-warm-coral palette reads as ocean and daylight
rather than startup-gradient. The result is calm, confident and legible from a
48 px launcher tile down to a favicon, and it collapses cleanly to one colour
when the situation demands it.

---

## Design directions

Two marks ship in this directory. **Pick one and delete the other before wiring launcher icons.**

| Direction | Files | Character |
|---|---|---|
| **Rounded (v1)** | `ephyra-mark.svg`, `ephyra-logo.svg` | Soft squircle tile, rounded page-panel “E”, coral wave. Friendly consumer-app read; closest to the Material 3 shape language. Adaptive-icon foreground provided. |
| **Geometric (v2)** | `ephyra-mark-geometric.svg`, `ephyra-logo-geometric.svg` | Hard corners, flat colour, one diagonal facet: a sharp page-bar “E” over a coral chevron. Media/streaming read — the Jellyfin-adjacent direction. |

Both share the palette and the core idea (a page-derived “E” plus a sea gesture), so they are
interchangeable without redoing the palette. The adaptive-icon foreground currently exists only for
the rounded direction; if the geometric mark wins, regenerate it from `ephyra-mark-geometric.svg`.
