# Proposal A: Summit

> **Rejected proposal.** This is not the Not at Home brand. The final identity is in [`../../README.md`](../../README.md).

A mountain range with a split-off snow cap and a low sun, with the name set underneath in a condensed, gear-label style. The closest to Columbia and The North Face.

**Type in the logo:** Barlow Condensed Bold for the name, Barlow SemiBold for the slogan. Text in the SVGs is converted to outlines, so no font is needed to display them.

## Brand colors

| Name | Hex | Use |
|---|---|---|
| Pine | `#1F4D3A` | Primary. Mountain, links, primary buttons (white text), headings accents. |
| Ember | `#E06A2C` | Accent. The sun, call-to-action highlights, active states. Use dark text on it. |

Plus black `#111111` and white `#FFFFFF`.

## Dark-mode tints

Lighter tints of the same hues, for logo and links on dark backgrounds (not extra brand colors).

| Name | Hex | Use |
|---|---|---|
| Pine light | `#7FB89A` | Pine for dark backgrounds (mark, links). |
| Ember text | `#C2531D` | Darker Ember when the accent must be text on white. |

## Contrast (WCAG 2.1)

| Text | Background | Ratio | Passes |
|---|---|---|---|
| white `#FFFFFF` | Pine `#1F4D3A` | 9.63:1 | AAA |
| black `#111111` | Ember `#E06A2C` | 5.64:1 | AA |
| Ember `#E06A2C` | white `#FFFFFF` | 3.35:1 | AA large/UI |
| Pine light `#7FB89A` | dark bg #121212 `#121212` | 8.23:1 | AAA |
| Ember `#E06A2C` | dark bg #121212 `#121212` | 5.59:1 | AA |
| Ember text `#C2531D` | white `#FFFFFF` | 4.62:1 | AA |

## Files

- `logo-full.svg`, `logo-full-dark.svg`, `logo-full-mono.svg`: full logo with slogan (light bg, dark bg, one color).
- `logo-horizontal*.svg`: compact one-line version for the top bar.
- `mark.svg`: symbol only. `favicon.svg`: simplified symbol on a tile for 16 to 32 px.
- `*-mono.svg` files use `currentColor`, so inline them and set the CSS `color`, or they render black.
