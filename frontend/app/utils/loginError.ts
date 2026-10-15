import type { ApiProblem } from '~/utils/problem'

/**
 * What to tell someone whose login attempt failed, or undefined when there is nothing to add to
 * the field errors already shown next to the inputs (a 400 validation failure).
 */
export function loginErrorMessage(problem: ApiProblem): string | undefined {
  switch (problem.status) {
    case 400:
      return undefined
    case 401:
      return 'Invalid username or password.'
    case 403:
      // Two kinds of 403: a banned account (has a code), or a rejected CSRF token (has none).
      return problem.code === 'ACCOUNT_BANNED'
        ? 'This account has been banned.'
        : 'Your session expired. Reload the page and try again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}
