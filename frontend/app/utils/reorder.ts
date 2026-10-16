/**
 * Swaps the item at `index` with its neighbor in `direction`. Returns the same array reference
 * when the move is out of bounds, so callers can tell a no-op apart from a real reorder.
 */
export function moveItem<T>(items: T[], index: number, direction: 'up' | 'down'): T[] {
  const target = direction === 'up' ? index - 1 : index + 1
  if (target < 0 || target >= items.length) return items
  const copy = [...items]
  ;[copy[index], copy[target]] = [copy[target]!, copy[index]!]
  return copy
}
