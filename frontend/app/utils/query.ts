/** A route query value as one string: `?a=1&a=2` gives an array, `?a` gives null. */
export function firstQueryValue(value: unknown): string | undefined {
  const first = Array.isArray(value) ? value[0] : value
  return typeof first === 'string' && first !== '' ? first : undefined
}

/** The `?page=` of a list (1-based): anything that isn't a positive whole number is page 1. */
export function parsePage(value: unknown): number {
  const text = firstQueryValue(value)
  if (!text || !/^\d{1,6}$/.test(text)) return 1
  return Math.max(Number(text), 1)
}
