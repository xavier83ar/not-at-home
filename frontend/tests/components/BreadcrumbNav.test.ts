import { describe, expect, it } from 'vitest'
import { mount, RouterLinkStub } from '@vue/test-utils'
import BreadcrumbNav from '~/components/BreadcrumbNav.vue'

function render(crumbs: { label: string; to?: string }[]) {
  return mount(BreadcrumbNav, {
    props: { crumbs },
    global: { stubs: { NuxtLink: RouterLinkStub } },
  })
}

describe('BreadcrumbNav', () => {
  it('starts at Home and links every crumb except the current page', () => {
    const wrapper = render([
      { label: 'Hiking', to: '/categories/hiking' },
      { label: 'Best trails in Patagonia' },
    ])
    const items = wrapper.findAll('li')
    expect(items.map((item) => item.text())).toEqual(['Home', 'Hiking', 'Best trails in Patagonia'])

    const links = wrapper.findAllComponents(RouterLinkStub)
    expect(links.map((link) => link.props('to'))).toEqual(['/', '/categories/hiking'])
  })

  it('marks the last crumb as the current page', () => {
    const wrapper = render([{ label: 'Categories' }])
    const items = wrapper.findAll('li')
    expect(items[0]!.attributes('aria-current')).toBeUndefined()
    expect(items[1]!.attributes('aria-current')).toBe('page')
    expect(wrapper.find('nav').attributes('aria-label')).toBe('Breadcrumb')
  })

  it('does not link the last crumb even when it has a target', () => {
    const wrapper = render([{ label: 'Hiking', to: '/categories/hiking' }])
    expect(wrapper.findAllComponents(RouterLinkStub)).toHaveLength(1)
  })
})
