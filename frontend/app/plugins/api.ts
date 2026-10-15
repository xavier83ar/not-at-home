import { appendResponseHeader } from 'h3'

/**
 * `$api` talks to the backend through `/api`.
 *
 * - In the browser it uses the same origin; the Nitro handler in `server/api/[...].ts` proxies it.
 * - During SSR it calls the backend directly, forwards the visitor's `Cookie` header (so the
 *   session is seen by the backend) and passes any `Set-Cookie` back to the browser.
 */
export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig()
  const event = useRequestEvent()
  const cookie = import.meta.server ? useRequestHeaders(['cookie']).cookie : undefined

  const api = $fetch.create({
    baseURL: import.meta.server ? config.backendUrl : undefined,
    headers: cookie ? { cookie } : undefined,
    onResponse({ response }) {
      if (import.meta.server && event) {
        for (const setCookie of response.headers.getSetCookie()) {
          appendResponseHeader(event, 'set-cookie', setCookie)
        }
      }
    },
  })

  return { provide: { api } }
})
