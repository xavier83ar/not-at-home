/** A route query value as one string: `?a=1&a=2` gives an array, `?a` gives null. */
export function firstQueryValue(value: unknown): string | undefined {
  const first = Array.isArray(value) ? value[0] : value
  return typeof first === 'string' && first !== '' ? first : undefined
}
