import type { ApiProblem } from '~/utils/problem'
import { rateLimitMessage } from '~/utils/rateLimit'

/** What to tell someone whose "confirm my email address" request failed. */
export function verifyErrorMessage(problem: ApiProblem): string {
  if (problem.status === 429) return rateLimitMessage(problem.retryAfterSeconds)
  // For a bad, used or expired link the backend's own sentence says it.
  return problem.detail ?? 'Something went wrong. Please try again.'
}
