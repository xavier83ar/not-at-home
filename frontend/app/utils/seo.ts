import type { components } from '~/types/api'
import { inlineText } from '~/utils/sanitize'

type EditorDocument = components['schemas']['EditorDocument']

export const SITE_NAME = 'Not at Home'
export const DEFAULT_DESCRIPTION = 'Not at Home: stories from the trail.'
/** Shown when a page has no image of its own (a small brand icon; see public/). */
export const DEFAULT_IMAGE = '/icon-192.png'

/** `https://site.example` + `/topics/1-x` (the site URL may or may not end with a slash). */
export function absoluteUrl(siteUrl: string, path: string): string {
  return `${siteUrl.replace(/\/+$/, '')}${path.startsWith('/') ? path : `/${path}`}`
}

/**
 * The address a page declares as its own. Pages of a list are separate pages for search engines,
 * so page 2 and up keep their `?page=`; page 1 is the plain path.
 */
export function canonicalPath(path: string, page = 1): string {
  return page > 1 ? `${path}?page=${page}` : path
}

/** Cuts at a word boundary where it can, with an ellipsis. */
export function truncate(text: string, max: number): string {
  if (text.length <= max) return text
  const cut = text.slice(0, max - 1)
  const lastSpace = cut.lastIndexOf(' ')
  return `${(lastSpace > max * 0.6 ? cut.slice(0, lastSpace) : cut).trimEnd()}…`
}

/* eslint-disable @typescript-eslint/no-explicit-any -- block data is checked by the backend */
function blockText(block: { type: string; data: Record<string, any> }): string {
  switch (block.type) {
    case 'paragraph':
    case 'header':
    case 'quote':
      return inlineText(String(block.data.text ?? ''))
    case 'list':
      return (Array.isArray(block.data.items) ? block.data.items : [])
        .map((item: any) =>
          inlineText(String(typeof item === 'string' ? item : (item?.content ?? ''))),
        )
        .filter(Boolean)
        .join(', ')
    default:
      return ''
  }
}
/* eslint-enable @typescript-eslint/no-explicit-any */

/** A short plain-text summary of a post, for meta descriptions (code and images are skipped). */
export function excerpt(document: EditorDocument | null | undefined, max = 160): string {
  const parts: string[] = []
  let length = 0
  for (const block of (document?.blocks ?? []) as {
    type: string
    data: Record<string, unknown>
  }[]) {
    const text = blockText(block)
    if (!text) continue
    parts.push(text)
    length += text.length + 1
    if (length >= max) break
  }
  return truncate(parts.join(' '), max)
}

export interface Breadcrumb {
  name: string
  /** Path on the site, e.g. `/categories/hiking`. */
  path: string
}

/** schema.org BreadcrumbList, as the JSON for a `application/ld+json` script. */
export function breadcrumbJsonLd(siteUrl: string, crumbs: Breadcrumb[]): string {
  const json = JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: crumbs.map((crumb, index) => ({
      '@type': 'ListItem',
      position: index + 1,
      name: crumb.name,
      item: absoluteUrl(siteUrl, crumb.path),
    })),
  })
  // Inside a <script>, "</script>" would end it early, whatever a topic title says.
  return json.replace(/</g, '\\u003c')
}
