import { describe, expect, it } from 'vitest'
import { RouterLinkStub, mount } from '@vue/test-utils'
import ProfilePostItem from '~/components/ProfilePostItem.vue'

describe('ProfilePostItem', () => {
  it('links the topic title to the post and shows its excerpt and category as text', () => {
    const wrapper = mount(ProfilePostItem, {
      props: {
        post: {
          postId: 42,
          topicId: 7,
          topicSlug: 'best-trails',
          topicTitle: 'Best <trails>',
          category: { id: 1, name: 'Hiking', slug: 'hiking' },
          excerpt: 'Bring <b>a sturdy</b> tent…',
          createdAt: '2026-01-31T10:00:00Z',
        },
      },
      global: { stubs: { NuxtLink: RouterLinkStub, TimeAgo: true } },
    })
    const links = wrapper.findAllComponents(RouterLinkStub)
    expect(links[0]!.props('to')).toBe('/posts/42')
    expect(links[0]!.text()).toBe('Best <trails>')
    expect(links[1]!.props('to')).toBe('/categories/hiking')
    // The excerpt is plain text: markup in it is shown, never run.
    expect(wrapper.find('.post-excerpt').text()).toBe('Bring <b>a sturdy</b> tent…')
    expect(wrapper.find('.post-excerpt').html()).not.toContain('<b>')
  })
})
