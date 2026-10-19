import { describe, expect, it } from 'vitest'
import { profilePath } from '~/utils/profileUrl'

describe('profilePath', () => {
  it('is /users/ plus the username', () => {
    expect(profilePath('alice')).toBe('/users/alice')
    expect(profilePath('trail_runner-9')).toBe('/users/trail_runner-9')
  })

  it('escapes anything that is not safe in a path', () => {
    expect(profilePath('a/b?c')).toBe('/users/a%2Fb%3Fc')
  })
})
