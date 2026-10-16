/** `/topics/123-best-trails-in-patagonia`: the id is what is looked up, the slug is cosmetic. */
export function topicPath(id: number, slug: string): string {
  return slug ? `/topics/${id}-${slug}` : `/topics/${id}`
}

/** A post's permanent link; it redirects to the right page of its topic (FR-4.6). */
export function postPath(id: number): string {
  return `/posts/${id}`
}

/** Reads the `123-some-slug` route parameter. Null when it doesn't start with an id. */
export function parseTopicParam(param: unknown): { id: number; slug: string } | null {
  if (typeof param !== 'string') return null
  const match = /^(\d{1,15})(?:-(.*))?$/.exec(param)
  if (!match) return null
  const id = Number(match[1])
  return id > 0 ? { id, slug: match[2] ?? '' } : null
}
