export type AccessRequirement = 'auth' | 'admin'

export type AccessDecision =
  { allow: true } | { allow: false; redirect: string } | { allow: false; forbidden: true }

/**
 * Who may open a page (the `auth` and `admin` route middleware). This only decides what the user
 * sees: the backend checks permissions on every request, whatever the pages do (NFR-2).
 *
 * - A guest is sent to the login page, which brings them back afterwards.
 * - Someone logged in without the right role gets a 403, not a redirect: logging in again won't help.
 */
export function decideAccess(
  requirement: AccessRequirement,
  user: { role: string } | null,
  fullPath: string,
): AccessDecision {
  if (!user) {
    return { allow: false, redirect: `/login?redirect=${encodeURIComponent(fullPath)}` }
  }
  if (requirement === 'admin' && user.role !== 'ADMIN') {
    return { allow: false, forbidden: true }
  }
  return { allow: true }
}
