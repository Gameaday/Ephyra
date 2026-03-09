# Theme Style Guide

This document describes the visual identity of the three branded themes in
the app: **Ephyra**, **Nagare**, and **Atolla**. Each theme goes beyond a
simple color swap — it defines its own shape language, spacing density, and
design philosophy through the `BrandedThemeConfig` system.

---

## How It Works

Every branded theme provides a `BrandedThemeConfig` (defined in
`presentation-core/.../theme/BrandedThemeConfig.kt`) that is exposed to the
entire Compose tree via the `LocalBrandedTheme` CompositionLocal. Components
read shape tokens (card radius, cover image radius, badge shape, etc.) from
this config so that the visual identity changes *beyond just color*.

All non-branded themes (Default, Catppuccin, Nord, etc.) use the default
config, which matches the existing `MihonShapes` radii.

---

## Ephyra — "Glassmorphic" Aesthetic

| Property          | Value         |
|-------------------|---------------|
| **Feeling**       | Translucent, modern, premium |
| **Colors**        | Electric Indigo (#4F46E5) & Cyan (#0891B2) |
| **Card radius**   | 20 dp (soft, airy) |
| **Cover radius**  | 16 dp |
| **Badge shape**   | Pill (50%) |
| **Sheet radius**  | 32 dp |
| **Dialog radius** | 32 dp |
| **Card elevation**| 0 dp (flat / frosted glass) |
| **Card border**   | 0 dp (no border) |
| **Grid spacing**  | 10 dp H × 10 dp V (more breathing room) |

### Design Notes

- Dark mode with "frosted glass" overlays and soft purple-to-teal gradients.
- Larger corner radii create an organic, jellyfish-bell-like silhouette.
- Zero elevation keeps cards flat — the translucent surface colors provide
  depth instead of shadows.
- Wider grid spacing gives the layout an "airy, premium" feel.

### Logo Concept

A minimalist, geometric jellyfish: a simple semi-circle (the bell) with
three clean, vertical lines of varying lengths beneath it.

---

## Nagare — "Minimalist Zen" Aesthetic

| Property          | Value         |
|-------------------|---------------|
| **Feeling**       | Fluid, lightweight, effortless |
| **Colors**        | Charcoal (#374151) & Mint (#059669) |
| **Card radius**   | 12 dp (clean, moderate) |
| **Cover radius**  | 8 dp |
| **Badge shape**   | Pill (50%) |
| **Sheet radius**  | 24 dp |
| **Dialog radius** | 24 dp |
| **Card elevation**| 0 dp |
| **Card border**   | 0 dp |
| **Grid spacing**  | 6 dp H × 6 dp V (tight, dense) |

### Design Notes

- Very "Material You" — lots of white space, clean sans-serif typography,
  monochromatic accents.
- Tighter grid spacing produces a denser layout that puts content front
  and center without visual clutter.
- Moderate corner radii keep things clean without being overly stylized.

### Logo Concept

A single, continuous "S-curve" line that subtly forms a lowercase "n".
Looks like a brush stroke or a gentle wave.

---

## Atolla — "System Hub" Aesthetic

| Property          | Value         |
|-------------------|---------------|
| **Feeling**       | Bold, stable, industrial |
| **Colors**        | Deep Sea Blue (#1E3A5F) & Amber (#F59E0B) |
| **Card radius**   | 6 dp (crisp, squared) |
| **Cover radius**  | 4 dp |
| **Badge shape**   | 4 dp (nearly square) |
| **Sheet radius**  | 16 dp |
| **Dialog radius** | 16 dp |
| **Card elevation**| 2 dp (tactile, raised) |
| **Card border**   | 1 dp |
| **Grid spacing**  | 8 dp H × 8 dp V (standard) |

### Design Notes

- High contrast with solid blocks of color and crisp borders.
- Small corner radii give cards a structured, "control panel" look.
- Subtle elevation and borders make cards feel tactile and organized.
- Library cards look like organized tiles on a dashboard.

### Logo Concept

Two concentric circles — the inner one is broken at the top (like a power
button or sync symbol). Represents a "Central Hub."

---

## Comparison

| Name   | Logo Concept       | Color Palette            | Primary Feeling       |
|--------|--------------------|--------------------------|----------------------|
| Ephyra | Geometric Bell     | Electric Indigo & Cyan   | Translucent / Modern |
| Nagare | Continuous Wave    | Charcoal & Mint          | Fluid / Lightweight  |
| Atolla | Concentric Rings   | Deep Sea Blue & Amber    | Reliable / Structured|

---

## For Developers

To read the current branded theme config in any `@Composable`:

```kotlin
val config = LocalBrandedTheme.current
// e.g., config.cardCornerRadius, config.cardElevation, config.name
```

Shape tokens (`ShapeTokens.card`, `ShapeTokens.coverImage`, etc.) already
delegate to the branded config, so most components adapt automatically.
