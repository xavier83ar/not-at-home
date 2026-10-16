import sanitizeHtml from 'sanitize-html'

/**
 * The inline HTML of a block (bold, italic, links...), cleaned again right before it is shown.
 * The backend already sanitizes what it stores (FR-4.4); this second pass means a bad row in the
 * database, or a bug there, still can't run script in a visitor's browser. It runs in Node, so
 * server-rendered pages are cleaned too. Keep the allow-list in step with the backend's
 * `InlineHtml`.
 *
 * Links to other sites open in a new tab and tell search engines not to follow them (they are
 * user content); links to this site (a path starting with one slash) are left alone.
 */
export function sanitizeInline(html: string): string {
  return sanitizeHtml(html, {
    allowedTags: ['b', 'strong', 'i', 'em', 'u', 'code', 'mark', 'br', 'a'],
    allowedAttributes: { a: ['href', 'rel', 'target'] },
    allowedSchemes: ['http', 'https', 'mailto'],
    allowProtocolRelative: false,
    transformTags: {
      a: (_tag, attribs) => {
        const href = attribs.href ?? ''
        const clean: Record<string, string> = {}
        if (href) clean.href = href
        if (/^https?:/i.test(href)) {
          clean.rel = 'nofollow noopener ugc'
          clean.target = '_blank'
        }
        return { tagName: 'a', attribs: clean }
      },
    },
  })
}

/** Plain text of inline HTML, e.g. for an image's alt text. */
export function inlineText(html: string): string {
  return sanitizeHtml(html, { allowedTags: [], allowedAttributes: {} }).replace(/\s+/g, ' ').trim()
}
