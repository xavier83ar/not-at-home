import { describe, expect, it } from 'vitest'
import { RouterLinkStub, mount } from '@vue/test-utils'
import AuthorName from '~/components/AuthorName.vue'

function render(author: { username: string; displayName: string; deleted: boolean }) {
  return mount(AuthorName, {
    props: { author },
    global: { stubs: { NuxtLink: RouterLinkStub } },
  })
}

describe('AuthorName', () => {
  it('links the display name to the profile', () => {
    const wrapper = render({ username: 'alice', displayName: 'Alice <A>', deleted: false })
    const link = wrapper.findComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/users/alice')
    expect(link.text()).toBe('Alice <A>')
  })

  it('shows a deleted user as plain text, with nowhere to go', () => {
    const wrapper = render({ username: 'deleted:7', displayName: 'Deleted user', deleted: true })
    expect(wrapper.findComponent(RouterLinkStub).exists()).toBe(false)
    expect(wrapper.text()).toBe('Deleted user')
  })
})
