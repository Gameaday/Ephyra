# Ephyra — Brand Assets

The Ephyra mark is a **juvenile jellyfish**.

That is not a metaphor: in the scyphozoan life cycle the **ephyra** is the juvenile stage — the
small, free-swimming young medusa that grows into the adult. So the app's name and its mark are the
same thing. It is also a warm nod to **Jellyfin** (the first non-extension source Ephyra targets),
and it is deliberately abstract — a creature in water, not a page or a panel — so it stays correct
when the app grows from manga to other reading material, and on to anime or video.

The drawing is a wide **bell** with **five splayed, tapered tentacles** and a **coral bell margin**.

## Files

| File | Grid | What it is |
|------|------|------------|
| `ephyra-symbol.svg` | 512 | The mark alone (teal + coral margin), transparent background. Use on light surfaces. |
| `ephyra-icon.svg` | 512 | The full app icon: the mark in Sea Paper on a deep-teal field. |
| `ephyra-mark-48.svg` | 512 | The **48 px form**: bell + three tentacles. |
| `ephyra-mark-24.svg` | 512 | The **24 px form**: bell alone with its coral margin. |
| `ephyra-adaptive-icon-foreground.svg` | 108 (66 safe) | Android adaptive-icon **foreground**. |
| `ephyra-adaptive-icon-background.svg` | 108 | Android adaptive-icon **background**. |
| `ephyra-monochrome.svg` | 108 (66 safe) | Single-colour layer for Material **themed icons**. |
| `ephyra-24dp.svg` | 24 | Material icon-grid version for in-app use. |
| `ephyra-wordmark.svg` | 764x252 | The "Ephyra" wordmark, Abyss Teal (light surfaces). |
| `ephyra-wordmark-on-dark.svg` | 764x252 | The wordmark in Sea Paper (dark surfaces). |

All files are standalone SVG: pure vector, no external fonts, no raster content, no JavaScript, no
`@import`, no external `href`.

## Palette

| Role | Name | Hex | Usage |
|------|------|-----|-------|
| Primary | **Abyss Teal** | `#0E4C5C` | Field; mark on light surfaces |
| Facet | **Abyss Deep** | `#0A3A47` | The single background facet |
| Accent | **Ember Coral** | `#FF7A59` | The bell margin only — reserve it |
| Neutral | **Sea Paper** | `#F5F1E8` | Mark on the teal field |

Contrast: Sea Paper on Abyss Teal is about **8.4:1** (WCAG AAA); Teal on white is about **9.5:1**.

## The size system — the mark blooms with size

The jellyfish is naturally variable, so the mark is drawn **three times** rather than shrunk. This is
a designed behaviour, not a compromise:

| Size | Drawing | Why |
|------|---------|-----|
| 512 / 48 | bell + **five** tentacles | full detail where it can be read |
| 48 | bell + **three** tentacles (`ephyra-mark-48.svg`) | the five become hairline gaps |
| 24 | **bell alone** with its coral margin (`ephyra-mark-24.svg`) | the tentacles merge into a smudge; the bell survives |

The coral accent is always the **bell margin**, never a tentacle, precisely so it survives the
reduction to 24 px. One accent, consistent at every size.

## Construction

- Built on a **512 grid**, mark inside a **384 keyline**.
- The bell is a **faceted dome** (eight points, hard corners) — geometric, not a curve.
- Five tentacles at equal **38-unit** spacing, tapering **26 → 14**, splayed **±26 units** outward.
  The splay is what makes it read as a living creature rather than a table or a plug.
- The coral margin is the **lower 20 units** of the bell, spanning its full width.
- The **24 dp** asset sits on the Material grid with a 20 dp live area (2 dp padding).
- The **adaptive** layers keep all essential geometry inside the central **66 × 66** of 108 × 108;
  the background facet is decorative and may bleed to the mask edge.

## Material compliance

- **Adaptive icon**: separate `foreground`, `background`, and `monochrome` layers at 108 × 108, with
a 66 dp safe zone (the outer 18 dp per side may be masked by the launcher).
- **Themed icons**: `ephyra-monochrome.svg` is a single-colour silhouette on transparency, tinted by
the system — do not bake colour into it.
- **Icon grid**: the 24 dp asset follows the Material keyline (square keyline, 2 dp padding).

## Wordmark

A geometric monoline sans (26-unit stroke, butt caps) drawn as stroked paths, not text: E is a spine
plus three bars; p and a use circular bowls; h has a round shoulder; y is two arms with a leftward
descender; r is a stem plus a quarter-round arm. Because it is geometry rather than `<text>`, it
renders identically everywhere and needs no font. For a lockup, set the symbol left of the wordmark
with clear space of 2X (X = one tentacle width).

## Notes

- The in-app Compose theme currently uses Electric Indigo `#4F46E5` + Cyan `#0891B2`. This mark is a
2.0 refresh; migrate the theme tokens to the palette above so app and mark match.
- Wiring the adaptive icon into `app/src/main/res/mipmap-anydpi-v26/` is a separate, reviewable step
(it changes the launcher icon).
