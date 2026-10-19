import { describe, expect, it } from 'vitest'
import { RouterLinkStub, mount } from '@vue/test-utils'
import SearchResultItem from '~/components/SearchResultItem.vue'

const result = (snippet: string) => ({
  postId: 42,
  topicId: 7,
  topicSlug: 'best-trails',
  topicTitle: 'Best <trails>',
  category: { id: 1, name: 'Hiking', slug: 'hiking' },
  author: {
    id: 3,
    username: 'alice',
    displayName: 'Alice',
    avatarUrl: null,
    role: 'USER' as const,
    deleted: false,
  },
  snippet,
  createdAt: '2026-01-31T10:00:00Z',
})

function render(snippet: string) {
  return mount(SearchResultItem, {
    props: { result: result(snippet) },
    global: { stubs: { NuxtLink: RouterLinkStub, UserAvatar: true, TimeAgo: true } },
  })
}

describe('SearchResultItem', () => {
  it('links the topic title to the post and shows who, where and the matched words', () => {
    const wrapper = render('Bring a sturdy <mark>tent</mark>')
    const links = wrapper.findAllComponents(RouterLinkStub)
    expect(links[0]!.props('to')).toBe('/posts/42')
    // The title is text, not HTML.
    expect(links[0]!.text()).toBe('Best <trails>')
    expect(links[1]!.props('to')).toBe('/users/alice')
    expect(links[1]!.text()).toBe('Alice')
    expect(links[2]!.props('to')).toBe('/categories/hiking')
    expect(wrapper.find('.search-snippet').html()).toContain('sturdy <mark>tent</mark>')
  })

  it('does not trust the backend: a hostile snippet keeps nothing but the marks', () => {
    const wrapper = render(
      '<mark onclick="x()">a</mark><script>alert(1)</script><img src=x onerror=y><a href="javascript:z">b</a>',
    )
    const html = wrapper.find('.search-snippet').html()
    expect(html).toContain('<mark>a</mark>')
    for (const bad of ['script', 'onclick', 'onerror', 'img', 'javascript', '<a']) {
      expect(html, bad).not.toContain(bad)
    }
  })

  it('shows escaped text as the text it was', () => {
    const wrapper = render('1 &lt; 2 &amp; <mark>tent</mark>')
    expect(wrapper.find('.search-snippet').text()).toBe('1 < 2 & tent')
  })
})
