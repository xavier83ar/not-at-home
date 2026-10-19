import type { ApiProblem } from '~/utils/problem'
import { rateLimitMessage } from '~/utils/rateLimit'

/**
 * What to tell someone whose password change, email change or account deletion failed, or
 * undefined when the field errors shown next to the inputs say it all (a wrong password, an
 * address that is taken). The one 409 is the last admin trying to delete their account (FR-8.5).
 */
export function accountErrorMessage(problem: ApiProblem): string | undefined {
  switch (problem.status) {
    case 429:
      return rateLimitMessage(problem.retryAfterSeconds)
    case 400:
      return Object.keys(problem.fieldErrors).length > 0
        ? undefined
        : 'Something went wrong. Please try again.'
    case 401:
      return 'Your session expired. Log in again and try again.'
    case 403:
      return 'Your session expired. Reload the page and try again.'
    case 409:
      return problem.code === 'LAST_ADMIN'
        ? (problem.detail ?? 'You are the last admin. Make someone else an admin first.')
        : 'Something went wrong. Please try again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}
