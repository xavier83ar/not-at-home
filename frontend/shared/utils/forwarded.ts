/**
 * What the backend is told about the visitor. Both the `/api` proxy and the server-side `$api`
 * (used while rendering) use it, so the backend sees the same client either way.
 *
 * The backend limits requests per client address (see README, "Rate limits"), and every request
 * reaches it from this server, so it must be told who the visitor is. It believes
 * `X-Forwarded-For` only because this server is the one that sets it, and it must therefore never
 * contain anything the visitor wrote: it is always *replaced* with a single address, never passed
 * through or extended.
 */
export interface ForwardedInput {
  /** The address of whoever connected to this server (the socket's peer). */
  peer: string | undefined
  /** The `X-Forwarded-For` header the visitor's request arrived with, if any. */
  forwardedFor: string | undefined
  /**
   * True only when a reverse proxy in front of this server sets `X-Forwarded-For` itself and
   * nothing else can reach this server directly (env `NUXT_TRUST_FORWARDED_FOR`). Then the peer is
   * that proxy, and the visitor's address is the one the proxy appended.
   */
  trustForwardedFor: boolean
}

/** The visitor's address, or undefined if it is unknown. */
export function resolveClientIp({
  peer,
  forwardedFor,
  trustForwardedFor,
}: ForwardedInput): string | undefined {
  if (trustForwardedFor && forwardedFor) {
    // A proxy appends the address it saw to the end of the list. Whatever is before it came from
    // the client or from earlier hops and can be forged, so only the last entry is believed.
    const last = forwardedFor
      .split(',')
      .map((entry) => entry.trim())
      .filter(Boolean)
      .at(-1)
    if (last) return last
  }
  return peer
}

/**
 * Headers for a request to the backend. `x-forwarded-for` is always set, to an empty value when
 * the address is unknown, so a header the visitor sent can never get through.
 */
export function forwardedHeaders(
  input: ForwardedInput & { host?: string; proto?: string },
): Record<string, string> {
  const headers: Record<string, string> = {
    'x-forwarded-for': resolveClientIp(input) ?? '',
  }
  if (input.host) headers['x-forwarded-host'] = input.host
  if (input.proto) headers['x-forwarded-proto'] = input.proto
  return headers
}
