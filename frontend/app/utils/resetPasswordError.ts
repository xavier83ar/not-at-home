import type { ApiProblem } from '~/utils/problem'

export interface ResetPasswordError {
  /** Shown above the form; undefined when the field errors beside the inputs say it all. */
  message: string | undefined
  /** The link itself is no good (bad, used or expired): offer to ask for a new one. */
  requestNewLink: boolean
}

/**
 * Two kinds of 400 come back: field errors for a bad password (the link still works, so the person
 * just fixes the field) and a plain message for a bad link.
 */
export function resetPasswordError(problem: ApiProblem): ResetPasswordError {
  if (problem.status === 400) {
    if (Object.keys(problem.fieldErrors).length > 0) {
      return { message: undefined, requestNewLink: false }
    }
    return {
      message: problem.detail ?? 'This password reset link is invalid or has expired.',
      requestNewLink: true,
    }
  }
  if (problem.status === 403) {
    return {
      message: 'Your session expired. Reload the page and try again.',
      requestNewLink: false,
    }
  }
  return { message: 'Something went wrong. Please try again.', requestNewLink: false }
}
