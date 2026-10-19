import { firstQueryValue } from '~/utils/query'

/** What the backend accepts for the keywords (FR-6.1). */
export const MIN_KEYWORDS = 3
export const MAX_KEYWORDS = 100

export type SearchSort = 'relevance' | 'date'

/** The search form's fields (FR-6.3, FR-6.4). An empty string means "not set". */
export interface SearchFilters {
  q: string
  /** A category slug. */
  category: string
  /** A username. */
  author: string
  /** Days like `2026-01-31`. */
  from: string
  to: string
  sort: SearchSort
}

/** Reads the filters out of a route's query. Anything unexpected counts as not set. */
export function parseSearchQuery(query: Record<string, unknown>): SearchFilters {
  return {
    q: firstQueryValue(query.q)?.trim() ?? '',
    category: firstQueryValue(query.category)?.trim() ?? '',
    author: firstQueryValue(query.author)?.trim() ?? '',
    from: firstQueryValue(query.from)?.trim() ?? '',
    to: firstQueryValue(query.to)?.trim() ?? '',
    sort: firstQueryValue(query.sort) === 'date' ? 'date' : 'relevance',
  }
}

/**
 * The query string for a search: only the filters that are set, and no `sort` for the default
 * order, so the URLs people share and the ones search engines see stay short and stable.
 */
export function searchRouteQuery(filters: Partial<SearchFilters>): Record<string, string> {
  const query: Record<string, string> = {}
  for (const key of ['q', 'category', 'author', 'from', 'to'] as const) {
    const value = filters[key]?.trim()
    if (value) query[key] = value
  }
  if (filters.sort === 'date') query.sort = 'date'
  return query
}
