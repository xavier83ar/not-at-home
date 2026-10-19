import type { ApiProblem } from '~/utils/problem'
import { rateLimitMessage } from '~/utils/rateLimit'

/**
 * What to tell someone whose profile or avatar change failed, or undefined when the field errors
 * shown next to the inputs say it all (a bad display name). Problems with the profile text are
 * phrased as a sentence about it, like `postErrorMessage` does for posts.
 */
export function profileErrorMessage(problem: ApiProblem): string | undefined {
  switch (problem.status) {
    case 429:
      return rateLimitMessage(problem.retryAfterSeconds)
    case 400: {
      const text = problem.fieldErrors.profileContent?.[0]
      if (text) {
        return /^(block|lists)/.test(text)
          ? `There is a problem with your profile text: ${text}.`
          : `Your profile text ${text}.`
      }
      if (problem.fieldErrors.uploadId) return 'That image could not be used. Upload it again.'
      return undefined
    }
    case 401:
      return 'Your session expired. Log in again and try again.'
    case 403:
      return 'Your session expired. Reload the page and try again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}
