# Proposal C: Trailhead

> **Rejected proposal.** This is not the Not at Home brand. The final identity is in [`../../README.md`](../../README.md).

Two peaks, with a winding trail cut through the front one up to the summit. It ties the mark to the slogan "Stories from the trail". Heavy geometric wordmark.

**Type in the logo:** Montserrat ExtraBold for the name, Barlow SemiBold for the slogan. Text in the SVGs is converted to outlines, so no font is needed to display them.

## Brand colors

| Name | Hex | Use |
|---|---|---|
| Rust | `#B4472A` | Primary. Front peak, primary buttons (white text), links. |
| Glacier | `#3D6F8E` | Secondary. Back peak, slogan, secondary buttons, info states. |

Plus black `#111111` and white `#FFFFFF`.

## Dark-mode tints

Lighter tints of the same hues, for logo and links on dark backgrounds (not extra brand colors).

| Name | Hex | Use |
|---|---|---|
| Rust light | `#E8834A` | Rust for dark backgrounds. |
| Glacier light | `#86B3D1` | Glacier for dark backgrounds. |

## Contrast (WCAG 2.1)

| Text | Background | Ratio | Passes |
|---|---|---|---|
| white `#FFFFFF` | Rust `#B4472A` | 5.42:1 | AA |
| white `#FFFFFF` | Glacier `#3D6F8E` | 5.44:1 | AA |
| Rust `#B4472A` | white `#FFFFFF` | 5.42:1 | AA |
| Glacier `#3D6F8E` | white `#FFFFFF` | 5.44:1 | AA |
| Rust light `#E8834A` | dark bg #121212 `#121212` | 6.94:1 | AA |
| Glacier light `#86B3D1` | dark bg #121212 `#121212` | 8.37:1 | AAA |

## Files

- `logo-full.svg`, `logo-full-dark.svg`, `logo-full-mono.svg`: full logo with slogan (light bg, dark bg, one color).
- `logo-horizontal*.svg`: compact one-line version for the top bar.
- `mark.svg`: symbol only. `favicon.svg`: simplified symbol on a tile for 16 to 32 px.
- `*-mono.svg` files use `currentColor`, so inline them and set the CSS `color`, or they render black.
