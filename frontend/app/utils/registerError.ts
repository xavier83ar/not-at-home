import type { ApiProblem } from '~/utils/problem'

/**
 * What to tell someone whose sign-up failed, or undefined when the field errors shown next to the
 * inputs say it all (a 400).
 */
export function registerErrorMessage(problem: ApiProblem): string | undefined {
  switch (problem.status) {
    case 400:
      return undefined
    case 403:
      return 'Your session expired. Reload the page and try again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}
