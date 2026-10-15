/**
 * "Too many attempts. Please wait 2 minutes and try again." The wait comes from the backend's
 * `Retry-After`, rounded up so that waiting that long is enough.
 */
export function rateLimitMessage(retryAfterSeconds?: number): string {
  if (
    retryAfterSeconds === undefined ||
    !Number.isFinite(retryAfterSeconds) ||
    retryAfterSeconds <= 0
  ) {
    return 'Too many attempts. Please wait a while and try again.'
  }
  const wait = (amount: number, unit: string) =>
    `Too many attempts. Please wait ${amount} ${unit}${amount === 1 ? '' : 's'} and try again.`
  if (retryAfterSeconds <= 60) return wait(Math.ceil(retryAfterSeconds), 'second')
  if (retryAfterSeconds < 3600) return wait(Math.ceil(retryAfterSeconds / 60), 'minute')
  return wait(Math.ceil(retryAfterSeconds / 3600), 'hour')
}
