import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import EditorContent from '~/components/EditorContent.vue'

type Block = { type: string; data: Record<string, unknown> }

function render(blocks: Block[]) {
  return mount(EditorContent, { props: { document: { blocks } } })
}

describe('EditorContent', () => {
  it('renders paragraphs with their sanitized inline formatting', () => {
    const wrapper = render([
      { type: 'paragraph', data: { text: 'Walk <b>far</b><script>alert(1)</script>' } },
    ])
    expect(wrapper.find('p').html()).toContain('Walk <b>far</b></p>')
    expect(wrapper.html()).not.toContain('script')
  })

  it('does not trust the backend: a hostile stored link is neutralised on render', () => {
    const wrapper = render([
      {
        type: 'paragraph',
        data: { text: '<a href="javascript:alert(1)" onclick="x()">click</a>' },
      },
    ])
    expect(wrapper.html()).not.toContain('javascript')
    expect(wrapper.html()).not.toContain('onclick')
  })

  it('puts headings below the page title', () => {
    const wrapper = render([
      { type: 'header', data: { text: 'One', level: 1 } },
      { type: 'header', data: { text: 'Four', level: 4 } },
    ])
    expect(wrapper.find('h2').text()).toBe('One')
    expect(wrapper.find('h5').text()).toBe('Four')
  })

  it('renders nested ordered lists with their numbering', () => {
    const wrapper = render([
      {
        type: 'list',
        data: {
          style: 'ordered',
          meta: { start: 3, counterType: 'lower-roman' },
          items: [
            { content: 'first', meta: {}, items: [{ content: 'nested', meta: {}, items: [] }] },
            { content: 'second', meta: {}, items: [] },
          ],
        },
      },
    ])
    const outer = wrapper.find('ol')
    expect(outer.attributes('start')).toBe('3')
    expect(outer.attributes('style')).toContain('lower-roman')
    expect(wrapper.findAll('li')).toHaveLength(3)
    expect(wrapper.find('ol ol li').text()).toBe('nested')
  })

  it('renders unordered lists and checklists', () => {
    const wrapper = render([
      {
        type: 'list',
        data: { style: 'unordered', items: [{ content: 'a', meta: {}, items: [] }] },
      },
      {
        type: 'list',
        data: {
          style: 'checklist',
          items: [
            { content: 'tent', meta: { checked: true }, items: [] },
            { content: 'stove', meta: { checked: false }, items: [] },
          ],
        },
      },
    ])
    expect(wrapper.findAll('ul')).toHaveLength(2)
    const boxes = wrapper.findAll('input[type=checkbox]')
    expect(boxes).toHaveLength(2)
    expect((boxes[0]!.element as HTMLInputElement).checked).toBe(true)
    expect((boxes[1]!.element as HTMLInputElement).checked).toBe(false)
    expect(boxes.every((box) => box.attributes('disabled') !== undefined)).toBe(true)
  })

  it('renders quotes with their caption', () => {
    const wrapper = render([
      {
        type: 'quote',
        data: { text: 'Nice one', caption: '<a href="/posts/12">bob</a>' },
      },
    ])
    expect(wrapper.find('blockquote p').text()).toBe('Nice one')
    expect(wrapper.find('blockquote footer a').attributes('href')).toBe('/posts/12')
  })

  it('shows code as text, never as markup', () => {
    const wrapper = render([{ type: 'code', data: { code: '<script>x()</script>' } }])
    expect(wrapper.find('pre code').text()).toBe('<script>x()</script>')
    expect(wrapper.find('pre script').exists()).toBe(false)
  })

  it('renders a delimiter', () => {
    expect(
      render([{ type: 'delimiter', data: {} }])
        .find('hr')
        .exists(),
    ).toBe(true)
  })

  it('renders images from this site only, with the caption as alt text', () => {
    const wrapper = render([
      {
        type: 'image',
        data: { file: { url: '/api/uploads/abc_1.png' }, caption: 'A <b>nice</b> view' },
      },
      { type: 'image', data: { file: { url: 'https://tracker.example/x.png' }, caption: 'bad' } },
    ])
    const images = wrapper.findAll('img')
    expect(images).toHaveLength(1)
    expect(images[0]!.attributes('src')).toBe('/api/uploads/abc_1.png')
    expect(images[0]!.attributes('alt')).toBe('A nice view')
  })

  it('applies the image border, background and stretch options', () => {
    const file = { url: '/api/uploads/abc_1.png' }
    const frames = render([
      { type: 'image', data: { file, caption: '' } },
      { type: 'image', data: { file, withBorder: true, withBackground: true, stretched: true } },
      { type: 'image', data: { file, withBorder: 'true', stretched: false } },
    ]).findAll('.image-frame')

    expect(frames).toHaveLength(3)
    // Posts saved before the options existed have none of them.
    expect(frames[0]!.classes()).toEqual(['image-frame', 'mb-2'])
    expect(frames[1]!.classes()).toEqual(
      expect.arrayContaining([
        'image-frame--border',
        'image-frame--background',
        'image-frame--stretched',
      ]),
    )
    // Only a real true turns an option on.
    expect(frames[2]!.classes()).toEqual(['image-frame', 'mb-2'])
  })

  it('renders link blocks as safe external links, and skips other addresses', () => {
    const wrapper = render([
      { type: 'link', data: { link: 'https://example.com/trail', title: 'Trail map' } },
      { type: 'link', data: { link: 'javascript:alert(1)', title: 'bad' } },
    ])
    const links = wrapper.findAll('a')
    expect(links).toHaveLength(1)
    expect(links[0]!.text()).toBe('Trail map')
    expect(links[0]!.attributes('rel')).toContain('nofollow')
  })

  it('skips blocks it does not know', () => {
    const wrapper = render([
      { type: 'table', data: { content: [['x']] } },
      { type: 'paragraph', data: { text: 'kept' } },
    ])
    expect(wrapper.text()).toBe('kept')
  })

  it('renders nothing for a missing document', () => {
    const wrapper = mount(EditorContent, { props: { document: null } })
    expect(wrapper.text()).toBe('')
  })
})
