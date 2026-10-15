export const CSRF_COOKIE = 'XSRF-TOKEN'
export const CSRF_HEADER = 'X-XSRF-TOKEN'

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

/** Requests that change state need the CSRF header; reads don't. */
export function needsCsrfToken(method: string | undefined): boolean {
  return !SAFE_METHODS.has((method ?? 'GET').toUpperCase())
}

/** Reads one cookie from a `Cookie` header value (or `document.cookie`). */
export function readCookie(name: string, cookieHeader: string | undefined): string | undefined {
  if (!cookieHeader) return undefined
  for (const part of cookieHeader.split(';')) {
    const separator = part.indexOf('=')
    if (separator === -1) continue
    if (part.slice(0, separator).trim() === name) {
      const value = part.slice(separator + 1).trim()
      return value ? decodeURIComponent(value) : undefined
    }
  }
  return undefined
}
