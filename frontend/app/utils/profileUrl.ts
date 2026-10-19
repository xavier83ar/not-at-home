/** `/users/alice`: usernames are unique ignoring case and can't change, so they are the key. */
export function profilePath(username: string): string {
  return `/users/${encodeURIComponent(username)}`
}
