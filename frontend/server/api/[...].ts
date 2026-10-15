import { proxyRequest } from 'h3'

/**
 * Proxies `/api/**` to the backend, so the browser only ever talks to this origin.
 * Done in a handler (not `routeRules`) so the backend URL can be set at runtime
 * with NUXT_BACKEND_URL, which the Docker image needs.
 */
export default defineEventHandler((event) => {
  const { backendUrl } = useRuntimeConfig(event)
  const host = getRequestHost(event, { xForwardedHost: true })
  const proto = getRequestProtocol(event, { xForwardedProto: true })

  return proxyRequest(
    event,
    `${backendUrl}/api${event.context.params?._ ? '/' + event.context.params._ : ''}`,
    {
      headers: { 'x-forwarded-host': host, 'x-forwarded-proto': proto },
    },
  )
})
