# Proposal B: Treeline

> **Rejected proposal.** This is not the Not at Home brand. The final identity is in [`../../README.md`](../../README.md).

The name stands on a plain, next to a small stand of pines and a rising sun. Rounded geometric letters give it a national-park signage feel.

**Type in the logo:** Josefin Sans Bold for the name, Barlow SemiBold for the slogan. Text in the SVGs is converted to outlines, so no font is needed to display them.

## Brand colors

| Name | Hex | Use |
|---|---|---|
| Lake | `#1C3B5A` | Primary. Trees, horizon line, top bar, primary buttons (white text), links. |
| Ochre | `#E3A72F` | Accent. The sun, badges, highlights, CTA on dark surfaces. Use dark text on it. |

Plus black `#111111` and white `#FFFFFF`.

## Dark-mode tints

Lighter tints of the same hues, for logo and links on dark backgrounds (not extra brand colors).

| Name | Hex | Use |
|---|---|---|
| Lake light | `#86B3D1` | Lake for dark backgrounds (mark, links). |

## Contrast (WCAG 2.1)

| Text | Background | Ratio | Passes |
|---|---|---|---|
| white `#FFFFFF` | Lake `#1C3B5A` | 11.52:1 | AAA |
| black `#111111` | Ochre `#E3A72F` | 8.84:1 | AAA |
| Ochre `#E3A72F` | Lake `#1C3B5A` | 5.40:1 | AA |
| Ochre `#E3A72F` | white `#FFFFFF` | 2.14:1 | decorative only |
| Lake light `#86B3D1` | dark bg #121212 `#121212` | 8.37:1 | AAA |
| Ochre `#E3A72F` | dark bg #121212 `#121212` | 8.77:1 | AAA |

## Files

- `logo-full.svg`, `logo-full-dark.svg`, `logo-full-mono.svg`: full logo with slogan (light bg, dark bg, one color).
- `logo-horizontal*.svg`: compact one-line version for the top bar.
- `mark.svg`: symbol only. `favicon.svg`: simplified symbol on a tile for 16 to 32 px.
- `*-mono.svg` files use `currentColor`, so inline them and set the CSS `color`, or they render black.
