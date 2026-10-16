/**
 * The page numbers to show in a pager: always the first and last page, and a few around the
 * current one, with `null` where pages are skipped. `window` is how many pages to show on each
 * side of the current page.
 */
export function pageItems(current: number, total: number, window = 2): (number | null)[] {
  const pages = new Set<number>([1, total])
  for (let page = current - window; page <= current + window; page++) {
    if (page >= 1 && page <= total) pages.add(page)
  }
  const sorted = [...pages].sort((a, b) => a - b)
  const items: (number | null)[] = []
  sorted.forEach((page, index) => {
    const previous = sorted[index - 1]
    if (previous !== undefined && page - previous === 2) items.push(previous + 1)
    else if (previous !== undefined && page - previous > 2) items.push(null)
    items.push(page)
  })
  return items
}
