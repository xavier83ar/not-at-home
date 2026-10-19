import { describe, expect, it } from 'vitest'
import { parseSearchQuery, searchRouteQuery } from '~/utils/search'

describe('parseSearchQuery', () => {
  it('reads every filter', () => {
    expect(
      parseSearchQuery({
        q: 'tent',
        category: 'hiking',
        author: 'alice',
        from: '2026-01-01',
        to: '2026-02-01',
        sort: 'date',
      }),
    ).toEqual({
      q: 'tent',
      category: 'hiking',
      author: 'alice',
      from: '2026-01-01',
      to: '2026-02-01',
      sort: 'date',
    })
  })

  it('treats what is missing or unexpected as not set', () => {
    expect(parseSearchQuery({ q: ['one', 'two'], sort: 'best', author: '  ' })).toEqual({
      q: 'one',
      category: '',
      author: '',
      from: '',
      to: '',
      sort: 'relevance',
    })
    expect(parseSearchQuery({})).toMatchObject({ q: '', sort: 'relevance' })
  })

  it('trims the keywords', () => {
    expect(parseSearchQuery({ q: '  tent  ' }).q).toBe('tent')
  })
})

describe('searchRouteQuery', () => {
  it('leaves out blank filters and the default order', () => {
    expect(
      searchRouteQuery({ q: 'tent', category: '', author: ' ', from: '', sort: 'relevance' }),
    ).toEqual({ q: 'tent' })
  })

  it('keeps the filters that are set, and the date order', () => {
    expect(
      searchRouteQuery({ q: ' tent ', category: 'hiking', to: '2026-02-01', sort: 'date' }),
    ).toEqual({ q: 'tent', category: 'hiking', to: '2026-02-01', sort: 'date' })
  })

  it('round-trips with parseSearchQuery', () => {
    const filters = {
      q: 'tent',
      category: 'hiking',
      author: 'alice',
      from: '2026-01-01',
      to: '2026-02-01',
      sort: 'date' as const,
    }
    expect(parseSearchQuery(searchRouteQuery(filters))).toEqual(filters)
  })
})
