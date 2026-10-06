# Ephyra — Brand Assets

The Ephyra symbol: a **geometric open book** — two folded planes meeting at a gutter — on a deep-teal
field, with a single coral wave beneath. It is deliberately **not a letterform**: the mark is an
object (a book) built from hard-edged planes, so it reads as *reading* at a glance and stays legible
from a 24 dp icon to a splash screen.

## Files

| File | Size / grid | What it is |
|------|-------------|------------|
| `ephyra-symbol.svg` | 512 | The mark alone, single colour, transparent background. Use on any light surface. |
| `ephyra-icon.svg` | 512 | The full app icon: mark + teal field + coral wave. |
| `ephyra-adaptive-icon-foreground.svg` | 108 (66 safe) | Android adaptive-icon **foreground** layer. |
| `ephyra-adaptive-icon-background.svg` | 108 | Android adaptive-icon **background** layer (flat teal + one facet). |
| `ephyra-monochrome.svg` | 108 (66 safe) | Single-colour layer for Material **themed icons**. |
| `ephyra-24dp.svg` | 24 | Material icon-grid version for in-app use. |

All files are standalone SVG: pure vector, no external fonts, no raster content, no JavaScript, no
`@import`, no external `href`.

## Palette

| Role | Name | Hex | Usage |
|------|------|-----|-------|
| Primary | **Abyss Teal** | `#0E4C5C` | Field, symbol on light surfaces |
| Facet | **Abyss Deep** | `#0A3A47` | The single background facet |
| Accent | **Ember Coral** | `#FF7A59` | The wave only — reserve it |
| Neutral | **Sea Paper** | `#F5F1E8` | Symbol on the teal field |

Contrast: Sea Paper on Abyss Teal ≈ **8.4:1** (WCAG AAA); Teal on white ≈ **9.5:1**.

## Construction

- Built on a **512 grid**, symbol inside a **384 keyline** (64 units clear on every side).
- The book is **two mirrored planes**; the **12-unit gutter** between them is the spine, and it is
  what makes the shape read as a book rather than a chevron. Do not close it.
- The **24 dp** version is drawn on the Material grid with a 20 dp live area (2 dp padding).
- The **adaptive icon** keeps all essential geometry inside the central **66 × 66** of the
  108 × 108 canvas; the background facet is decorative and may bleed to the mask edge.

## Material compliance

- **Adaptive icon**: separate `foreground`, `background`, and `monochrome` layers at 108 × 108, with
  a 66 dp safe zone (Google Play requires the 66 dp centre; the outer 18 dp per side may be masked).
- **Themed icons**: `ephyra-monochrome.svg` is a single-colour silhouette on transparency, tinted by
  the system — do not bake colour into it.
- **Icon grid**: the 24 dp asset follows the Material keyline (square keyline, 2 dp padding).

## Notes

- The in-app Compose theme currently uses Electric Indigo `#4F46E5` + Cyan `#0891B2`. This mark is a
  2.0 refresh; migrate the theme tokens to the palette above so app and mark match.
- Wiring the adaptive icon into `app/src/main/res/mipmap-anydpi-v26/` is a separate, reviewable step
  (it changes the launcher icon).
