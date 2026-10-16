import type { ApiProblem } from '~/utils/problem'
import { rateLimitMessage } from '~/utils/rateLimit'

/**
 * For the create/edit form: field problems (400) are shown next to the inputs, so there is nothing
 * more to say about them here.
 */
export function categoryFormErrorMessage(problem: ApiProblem): string | undefined {
  switch (problem.status) {
    case 429:
      return rateLimitMessage(problem.retryAfterSeconds)
    case 400:
      return undefined
    case 403:
      return 'Your session expired. Reload the page and try again.'
    case 404:
      return 'This category no longer exists.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

/**
 * For reorder and delete, which have no form fields to show a 400's errors next to.
 */
export function categoryActionErrorMessage(problem: ApiProblem): string {
  if (problem.status === 429) return rateLimitMessage(problem.retryAfterSeconds)
  const fieldMessages = Object.values(problem.fieldErrors).flat()
  if (fieldMessages.length) return fieldMessages.join(' ')
  if (problem.status === 403) return 'Your session expired. Reload the page and try again.'
  if (problem.status === 404) return 'This category no longer exists.'
  // 409: the category still has topics; the backend says so in plain words.
  if (problem.status === 409 && problem.detail) return problem.detail
  return 'Something went wrong. Please try again.'
}
