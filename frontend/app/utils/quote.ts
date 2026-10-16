import type { components } from '~/types/api'
import { inlineText } from '~/utils/sanitize'

type EditorDocument = components['schemas']['EditorDocument']
type Item = { content?: unknown; items?: Item[] }

const MAX_QUOTE_LENGTH = 300

export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function listText(items: Item[] | undefined): string[] {
  return (items ?? []).flatMap((item) => [
    inlineText(String(item.content ?? '')),
    ...listText(item.items),
  ])
}

/**
 * The plain text of a post, cut to a reasonable length for quoting. Code and images are left out;
 * a quote of a quote keeps only what was said, not who said it first.
 */
export function quoteText(document: EditorDocument | null | undefined): string {
  const parts: string[] = []
  for (const block of document?.blocks ?? []) {
    const data = block.data as Record<string, unknown>
    if (block.type === 'paragraph' || block.type === 'header' || block.type === 'quote') {
      parts.push(inlineText(String(data.text ?? '')))
    } else if (block.type === 'list') {
      parts.push(...listText(data.items as Item[]))
    }
  }
  const text = parts.filter(Boolean).join(' ')
  if (text.length <= MAX_QUOTE_LENGTH) return text
  const cut = text.slice(0, MAX_QUOTE_LENGTH)
  const lastSpace = cut.lastIndexOf(' ')
  return `${(lastSpace > MAX_QUOTE_LENGTH / 2 ? cut.slice(0, lastSpace) : cut).trimEnd()}…`
}

/**
 * An Editor.js quote block for replying to a post (FR-4.5): the quoted text, with the author's
 * name as a link back to the post as its caption. Everything is escaped: the backend's sanitizer
 * keeps the link, since it is a path on this site.
 */
export function quoteBlockData(post: {
  id: number
  author: { displayName: string }
  content?: EditorDocument | null
}) {
  return {
    text: escapeHtml(quoteText(post.content)),
    caption: `<a href="/posts/${post.id}">${escapeHtml(post.author.displayName)}</a>`,
    alignment: 'left',
  }
}
