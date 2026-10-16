import { describe, expect, it } from 'vitest'
import { pageItems } from '~/utils/pagination'

describe('pageItems', () => {
  it('lists every page when there are few', () => {
    expect(pageItems(1, 3)).toEqual([1, 2, 3])
    expect(pageItems(2, 5)).toEqual([1, 2, 3, 4, 5])
  })

  it('shows one page', () => {
    expect(pageItems(1, 1)).toEqual([1])
  })

  it('skips pages far from the current one, keeping the first and the last', () => {
    expect(pageItems(10, 20)).toEqual([1, null, 8, 9, 10, 11, 12, null, 20])
  })

  it('does not hide a single missing page behind an ellipsis', () => {
    expect(pageItems(1, 4, 1)).toEqual([1, 2, 3, 4])
    expect(pageItems(1, 5, 1)).toEqual([1, 2, null, 5])
    expect(pageItems(5, 9, 1)).toEqual([1, null, 4, 5, 6, null, 9])
    expect(pageItems(4, 9, 1)).toEqual([1, 2, 3, 4, 5, null, 9])
  })

  it('stays within the first and last pages', () => {
    expect(pageItems(1, 20)).toEqual([1, 2, 3, null, 20])
    expect(pageItems(20, 20)).toEqual([1, null, 18, 19, 20])
  })
})
