import { describe, expect, it } from 'vitest'
import { decideAccess } from '~/utils/access'
import { safeRedirect } from '~/utils/problem'

const user = { role: 'USER' }
const admin = { role: 'ADMIN' }

describe('decideAccess', () => {
  it('sends guests to the login page and back afterwards, keeping the whole path', () => {
    expect(decideAccess('auth', null, '/settings')).toEqual({
      allow: false,
      redirect: '/login?redirect=%2Fsettings',
    })
    expect(decideAccess('admin', null, '/admin/users?page=2&q=a b')).toEqual({
      allow: false,
      redirect: '/login?redirect=%2Fadmin%2Fusers%3Fpage%3D2%26q%3Da%20b',
    })
  })

  it('lets any logged-in user into pages that only need a login', () => {
    expect(decideAccess('auth', user, '/settings')).toEqual({ allow: true })
    expect(decideAccess('auth', admin, '/settings')).toEqual({ allow: true })
  })

  it('forbids admin pages to users who are not admins, instead of redirecting', () => {
    expect(decideAccess('admin', user, '/admin')).toEqual({ allow: false, forbidden: true })
  })

  it('lets admins in everywhere', () => {
    expect(decideAccess('admin', admin, '/admin')).toEqual({ allow: true })
  })

  it('works with the redirect parameter the login page accepts', () => {
    const decision = decideAccess('auth', null, '/topics/1?page=2')
    const target = new URL((decision as { redirect: string }).redirect, 'http://x')
    expect(safeRedirect(target.searchParams.get('redirect'))).toBe('/topics/1?page=2')
  })
})
