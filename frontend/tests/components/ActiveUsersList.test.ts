import { describe, expect, it } from 'vitest'
import { RouterLinkStub, mount } from '@vue/test-utils'
import ActiveUsersList from '~/components/ActiveUsersList.vue'

const user = (id: number, displayName: string, avatarUrl: string | null = null) => ({
  id,
  username: displayName.toLowerCase(),
  displayName,
  avatarUrl,
  role: 'USER' as const,
  deleted: false,
})

describe('ActiveUsersList', () => {
  it('lists the users in the order given, with their post counts', () => {
    const wrapper = mount(ActiveUsersList, {
      props: {
        users: [
          { user: user(1, 'Alice'), postCount: 12 },
          { user: user(2, 'Bob'), postCount: 1 },
        ],
      },
      global: { stubs: { NuxtLink: RouterLinkStub } },
    })
    const rows = wrapper.findAll('li')
    expect(rows).toHaveLength(2)
    expect(rows[0]!.text()).toContain('Alice')
    expect(rows[0]!.text()).toContain('12 posts')
    expect(rows[1]!.text()).toContain('Bob')
    expect(rows[1]!.text()).toContain('1 post')
    expect(rows[1]!.text()).not.toContain('1 posts')
  })

  it('links each name to the profile', () => {
    const wrapper = mount(ActiveUsersList, {
      props: { users: [{ user: user(1, 'Alice'), postCount: 12 }] },
      global: { stubs: { NuxtLink: RouterLinkStub } },
    })
    expect(wrapper.findComponent(RouterLinkStub).props('to')).toBe('/users/alice')
  })

  it('shows initials for people without an avatar and the picture for those with one', () => {
    const wrapper = mount(ActiveUsersList, {
      props: {
        users: [
          { user: user(1, 'Alice Smith'), postCount: 2 },
          { user: user(2, 'Bob', '/api/uploads/bob.png'), postCount: 1 },
        ],
      },
      global: { stubs: { NuxtLink: RouterLinkStub } },
    })
    expect(wrapper.text()).toContain('AS')
    expect(wrapper.find('img').attributes('src')).toBe('/api/uploads/bob.png')
  })
})
