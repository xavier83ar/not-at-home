import type { ApiProblem } from '~/utils/problem'
import { rateLimitMessage } from '~/utils/rateLimit'

/**
 * What to tell someone whose post, reply, edit or delete failed. A 400 about the content is
 * phrased as a sentence about "your post"; field errors for other fields (title, category) are
 * shown next to their inputs, so they get no message here.
 */
export function postErrorMessage(problem: ApiProblem): string | undefined {
  switch (problem.status) {
    case 429:
      return rateLimitMessage(problem.retryAfterSeconds)
    case 400: {
      const content = problem.fieldErrors.content?.[0]
      if (!content) return undefined
      return /^(block|lists)/.test(content)
        ? `There is a problem with your post: ${content}.`
        : `Your post ${content}.`
    }
    case 401:
      return 'Your session expired. Log in again and try again.'
    case 403:
      if (problem.code === 'EMAIL_NOT_VERIFIED') return 'Verify your email address to post.'
      if (problem.code === 'TOPIC_LOCKED') return 'This topic is locked. Nobody can reply to it.'
      if (problem.detail?.startsWith('You can only')) return problem.detail
      return 'Your session expired. Reload the page and try again.'
    case 404:
      return 'This topic or post no longer exists.'
    default:
      return 'Something went wrong. Please try again.'
  }
}
