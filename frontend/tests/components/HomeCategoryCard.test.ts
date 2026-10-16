import { describe, expect, it } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import HomeCategoryCard from '~/components/HomeCategoryCard.vue'

const category = {
  id: 1,
  name: 'Hiking',
  slug: 'hiking',
  description: 'Trails',
  coverImageUrl: '/api/uploads/abc.jpg',
  position: 0,
  topicCount: 3,
  postCount: 9,
}

function render(overrides = {}) {
  return mount(HomeCategoryCard, {
    props: { category: { ...category, ...overrides } },
    global: { stubs: { NuxtLink: RouterLinkStub } },
  })
}

describe('HomeCategoryCard', () => {
  it('links to the category and shows its name and topic count', () => {
    const wrapper = render()
    expect(wrapper.findComponent(RouterLinkStub).props('to')).toBe('/categories/hiking')
    expect(wrapper.text()).toContain('Hiking')
    expect(wrapper.text()).toContain('3 topics')
  })

  it('uses the cover image as the background', () => {
    expect(render().html()).toContain('/api/uploads/abc.jpg')
  })

  it('works without a cover image and says "1 topic" in the singular', () => {
    const wrapper = render({ coverImageUrl: null, topicCount: 1 })
    expect(wrapper.html()).not.toContain('background-image')
    expect(wrapper.text()).toContain('1 topic')
    expect(wrapper.text()).not.toContain('1 topics')
  })
})
