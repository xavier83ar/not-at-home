import type { ApiProblem } from '~/utils/problem'
import { rateLimitMessage } from '~/utils/rateLimit'

/** What to tell someone whose "resend the verification email" request failed. */
export function resendErrorMessage(problem: ApiProblem): string {
  if (problem.status === 429) return rateLimitMessage(problem.retryAfterSeconds)
  return 'Could not send the email. Please try again in a moment.'
}
