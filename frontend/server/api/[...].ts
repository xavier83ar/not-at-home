import { proxyRequest } from 'h3'

/**
 * Proxies `/api/**` to the backend, so the browser only ever talks to this origin.
 * Done in a handler (not `routeRules`) so the backend URL can be set at runtime
 * with NUXT_BACKEND_URL, which the Docker image needs.
 *
 * It also tells the backend who the visitor is (`X-Forwarded-For`), which the backend's rate
 * limits depend on; see `shared/utils/forwarded.ts`.
 */
export default defineEventHandler((event) => {
  const { backendUrl, trustForwardedFor } = useRuntimeConfig(event)

  // `event.path` keeps the `/api` prefix and the query string.
  return proxyRequest(event, `${backendUrl}${event.path}`, {
    headers: forwardedHeaders({
      peer: event.node.req.socket.remoteAddress,
      forwardedFor: getRequestHeader(event, 'x-forwarded-for'),
      trustForwardedFor,
      host: getRequestHost(event, { xForwardedHost: true }),
      proto: getRequestProtocol(event, { xForwardedProto: true }),
    }),
  })
})
