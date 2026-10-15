/**
 * The cookies one server-side render sends to the backend, kept up to date as the backend answers.
 *
 * Why: the "remember me" cookie changes every time it is used. If a render makes two backend
 * calls and the second still sent the visitor's original cookies, the backend would see the old
 * token used a second time, take it for theft and revoke the user's remembered logins. So cookies
 * the first call sets (the new session, the new token) must be sent with the next call.
 */
export type CookieJar = Map<string, string>

/** Values stay exactly as received (still encoded), so they can be sent back unchanged. */
export function parseCookieHeader(header: string | undefined): CookieJar {
  const jar: CookieJar = new Map()
  for (const part of (header ?? '').split(';')) {
    const separator = part.indexOf('=')
    if (separator <= 0) continue
    jar.set(part.slice(0, separator).trim(), part.slice(separator + 1).trim())
  }
  return jar
}

/** The `Cookie` header value, or undefined when the jar is empty. */
export function serializeCookieJar(jar: CookieJar): string | undefined {
  if (jar.size === 0) return undefined
  return [...jar].map(([name, value]) => `${name}=${value}`).join('; ')
}

/**
 * Applies `Set-Cookie` headers in order, like a browser: a cookie is added or replaced, and one
 * that is expired (empty value, `Max-Age` of zero or less, or a past `Expires`) is removed.
 */
export function applySetCookies(jar: CookieJar, setCookies: string[], now = Date.now()): void {
  for (const setCookie of setCookies) {
    const [pair = '', ...attributes] = setCookie.split(';')
    const separator = pair.indexOf('=')
    if (separator <= 0) continue
    const name = pair.slice(0, separator).trim()
    const value = pair.slice(separator + 1).trim()
    if (value === '' || isExpired(attributes, now)) {
      jar.delete(name)
    } else {
      jar.set(name, value)
    }
  }
}

function isExpired(attributes: string[], now: number): boolean {
  for (const attribute of attributes) {
    const separator = attribute.indexOf('=')
    if (separator === -1) continue
    const key = attribute.slice(0, separator).trim().toLowerCase()
    const value = attribute.slice(separator + 1).trim()
    if (key === 'max-age' && Number(value) <= 0) return true
    if (key === 'expires' && Date.parse(value) < now) return true
  }
  return false
}
