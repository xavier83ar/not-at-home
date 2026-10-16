import { describe, expect, it } from 'vitest'
import { parseTopicParam, postPath, topicPath } from '~/utils/topicUrl'

describe('topicPath', () => {
  it('joins the id and the slug', () => {
    expect(topicPath(123, 'best-trails-in-patagonia')).toBe('/topics/123-best-trails-in-patagonia')
  })

  it('works without a slug', () => {
    expect(topicPath(7, '')).toBe('/topics/7')
  })
})

describe('postPath', () => {
  it('is the permanent link', () => {
    expect(postPath(45)).toBe('/posts/45')
  })
})

describe('parseTopicParam', () => {
  it('reads the id and the slug', () => {
    expect(parseTopicParam('123-best-trails')).toEqual({ id: 123, slug: 'best-trails' })
  })

  it('accepts a bare id', () => {
    expect(parseTopicParam('42')).toEqual({ id: 42, slug: '' })
  })

  it('keeps dashes and digits inside the slug', () => {
    expect(parseTopicParam('5-top-10-trails-2026')).toEqual({ id: 5, slug: 'top-10-trails-2026' })
  })

  it('rejects anything that does not start with an id', () => {
    for (const value of ['best-trails', '-5', '0', '0-x', 'abc', '', '12abc', undefined, ['1']]) {
      expect(parseTopicParam(value), String(value)).toBeNull()
    }
  })
})
