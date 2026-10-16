import type { ApiProblem } from '~/utils/problem'
import { rateLimitMessage } from '~/utils/rateLimit'

/** For the upload widget; reused later by avatars (M7) and post images (M4). */
export function uploadErrorMessage(problem: ApiProblem): string {
  if (problem.status === 429) return rateLimitMessage(problem.retryAfterSeconds)
  const fileMessages = problem.fieldErrors.file
  if (fileMessages?.length) return fileMessages[0]!
  if (problem.status === 401) return 'You need to be logged in to upload a file.'
  return 'Could not upload the file. Please try again.'
}
