import { appendResponseHeader } from 'h3'

/**
 * `$api` talks to the backend through `/api`.
 *
 * - In the browser it uses the same origin; the Nitro handler in `server/api/[...].ts` proxies it.
 * - During SSR it calls the backend directly, sends the visitor's cookies (so the session is seen
 *   by the backend) and passes every `Set-Cookie` back to the browser. It also applies those
 *   `Set-Cookie`s to the cookies it sends next: the remember-me token changes on use, so a second
 *   call in the same render must not present the old one (see `utils/cookieJar.ts`).
 * - Requests that change state carry the CSRF token in the `X-XSRF-TOKEN` header. The token is
 *   read from its cookie on every request, because the backend replaces it at login and logout.
 */
export default defineNuxtPlugin({
  name: 'api',
  setup() {
    const config = useRuntimeConfig()
    const event = useRequestEvent()
    // Only exists during SSR; in the browser the cookies are the browser's own.
    const jar = import.meta.server
      ? parseCookieHeader(useRequestHeaders(['cookie']).cookie)
      : undefined

    /** The backend only issues the cookie on demand, so ask for it when it's missing. */
    async function csrfToken(): Promise<string | undefined> {
      if (jar) return readCookie(CSRF_COOKIE, serializeCookieJar(jar))
      const existing = readCookie(CSRF_COOKIE, document.cookie)
      if (existing) return existing
      await $fetch('/api/csrf')
      return readCookie(CSRF_COOKIE, document.cookie)
    }

    const api = $fetch.create({
      baseURL: import.meta.server ? config.backendUrl : undefined,
      async onRequest({ options }) {
        const headers = new Headers(options.headers)
        const cookie = jar && serializeCookieJar(jar)
        if (cookie) headers.set('cookie', cookie)
        if (needsCsrfToken(options.method)) {
          const token = await csrfToken()
          if (token) headers.set(CSRF_HEADER, token)
        }
        options.headers = headers
      },
      onResponse({ response }) {
        if (jar && event) {
          const setCookies = response.headers.getSetCookie()
          applySetCookies(jar, setCookies)
          for (const setCookie of setCookies) {
            appendResponseHeader(event, 'set-cookie', setCookie)
          }
        }
      },
    })

    return { provide: { api } }
  },
})
