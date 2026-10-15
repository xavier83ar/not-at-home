import { appendResponseHeader } from 'h3'

/**
 * `$api` talks to the backend through `/api`.
 *
 * - In the browser it uses the same origin; the Nitro handler in `server/api/[...].ts` proxies it.
 * - During SSR it calls the backend directly, forwards the visitor's `Cookie` header (so the
 *   session is seen by the backend) and passes any `Set-Cookie` back to the browser.
 * - Requests that change state carry the CSRF token in the `X-XSRF-TOKEN` header. The token is
 *   read from its cookie on every request, because the backend replaces it at login and logout.
 */
export default defineNuxtPlugin({
  name: 'api',
  setup() {
    const config = useRuntimeConfig()
    const event = useRequestEvent()
    const cookie = import.meta.server ? useRequestHeaders(['cookie']).cookie : undefined

    /** The backend only issues the cookie on demand, so ask for it when it's missing. */
    async function csrfToken(): Promise<string | undefined> {
      if (import.meta.server) return readCookie(CSRF_COOKIE, cookie)
      const existing = readCookie(CSRF_COOKIE, document.cookie)
      if (existing) return existing
      await $fetch('/api/csrf')
      return readCookie(CSRF_COOKIE, document.cookie)
    }

    const api = $fetch.create({
      baseURL: import.meta.server ? config.backendUrl : undefined,
      headers: cookie ? { cookie } : undefined,
      async onRequest({ options }) {
        if (!needsCsrfToken(options.method)) return
        const token = await csrfToken()
        if (token) {
          const headers = new Headers(options.headers)
          headers.set(CSRF_HEADER, token)
          options.headers = headers
        }
      },
      onResponse({ response }) {
        if (import.meta.server && event) {
          for (const setCookie of response.headers.getSetCookie()) {
            appendResponseHeader(event, 'set-cookie', setCookie)
          }
        }
      },
    })

    return { provide: { api } }
  },
})
