export interface ApiProblem {
  /** HTTP status, or undefined when the request never got a response (network error). */
  status: number | undefined
  /** The backend's `detail` text, when there is one. */
  detail: string | undefined
  /**
   * A machine-readable reason some errors carry, e.g. `EMAIL_NOT_VERIFIED`. Lets the frontend tell
   * two 403s apart: a rejected CSRF token has none.
   */
  code: string | undefined
  /** For 429 (too many requests): how long to wait, in seconds, when the backend said. */
  retryAfterSeconds?: number
  /** Validation messages keyed by field name; empty unless the backend sent field errors. */
  fieldErrors: Record<string, string[]>
}

interface FetchErrorLike {
  status?: number
  statusCode?: number
  data?: { detail?: unknown; code?: unknown; retryAfterSeconds?: unknown; errors?: unknown }
}

/** Reads the backend's RFC 9457 `ProblemDetail` out of an error thrown by `$api`. */
export function parseProblem(error: unknown): ApiProblem {
  const e = (error ?? {}) as FetchErrorLike
  const data = e.data ?? {}
  const fieldErrors: Record<string, string[]> = {}
  if (data.errors && typeof data.errors === 'object') {
    for (const [field, messages] of Object.entries(data.errors)) {
      if (Array.isArray(messages)) fieldErrors[field] = messages.map(String)
    }
  }
  return {
    status: e.status ?? e.statusCode,
    detail: typeof data.detail === 'string' ? data.detail : undefined,
    code: typeof data.code === 'string' ? data.code : undefined,
    retryAfterSeconds:
      typeof data.retryAfterSeconds === 'number' ? data.retryAfterSeconds : undefined,
    fieldErrors,
  }
}

/**
 * Only follow redirects to a path on this site. `//evil.example` and `https://...` would turn the
 * `?redirect=` parameter into an open redirect.
 */
export function safeRedirect(target: unknown, fallback = '/'): string {
  if (typeof target !== 'string') return fallback
  if (!target.startsWith('/') || target.startsWith('//') || target.includes('\\')) return fallback
  return target
}
