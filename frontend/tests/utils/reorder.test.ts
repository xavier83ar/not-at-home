import { describe, expect, it } from 'vitest'
import { moveItem } from '~/utils/reorder'

describe('moveItem', () => {
  it('swaps with the previous item when moving up', () => {
    expect(moveItem(['a', 'b', 'c'], 1, 'up')).toEqual(['b', 'a', 'c'])
  })

  it('swaps with the next item when moving down', () => {
    expect(moveItem(['a', 'b', 'c'], 1, 'down')).toEqual(['a', 'c', 'b'])
  })

  it('does nothing when the first item tries to move up', () => {
    const items = ['a', 'b', 'c']
    expect(moveItem(items, 0, 'up')).toBe(items)
  })

  it('does nothing when the last item tries to move down', () => {
    const items = ['a', 'b', 'c']
    expect(moveItem(items, 2, 'down')).toBe(items)
  })

  it('does not mutate the original array', () => {
    const items = ['a', 'b', 'c']
    moveItem(items, 0, 'down')
    expect(items).toEqual(['a', 'b', 'c'])
  })
})
