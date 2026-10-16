import { describe, expect, it } from 'vitest'
import { escapeHtml, quoteBlockData, quoteText } from '~/utils/quote'

const doc = (...blocks: { type: string; data: Record<string, unknown> }[]) => ({ blocks })

describe('quoteText', () => {
  it('joins the text of paragraphs, headers, quotes and lists', () => {
    const text = quoteText(
      doc(
        { type: 'header', data: { text: 'Day <b>one</b>' } },
        { type: 'paragraph', data: { text: 'We left&nbsp;early.' } },
        { type: 'quote', data: { text: 'Said before', caption: 'someone' } },
        {
          type: 'list',
          data: {
            style: 'unordered',
            items: [{ content: 'water', items: [{ content: 'a lot', items: [] }] }],
          },
        },
      ),
    )
    expect(text).toBe('Day one We left early. Said before water a lot')
  })

  it('leaves out code and images', () => {
    const text = quoteText(
      doc(
        { type: 'code', data: { code: 'rm -rf /' } },
        { type: 'image', data: { file: { url: '/api/uploads/a.png' }, caption: 'pic' } },
        { type: 'paragraph', data: { text: 'text' } },
      ),
    )
    expect(text).toBe('text')
  })

  it('cuts long posts at a word and adds an ellipsis', () => {
    const text = quoteText(doc({ type: 'paragraph', data: { text: 'word '.repeat(200) } }))
    expect(text.length).toBeLessThanOrEqual(301)
    expect(text.endsWith('word…')).toBe(true)
  })

  it('copes with a missing document', () => {
    expect(quoteText(null)).toBe('')
    expect(quoteText(undefined)).toBe('')
  })
})

describe('quoteBlockData', () => {
  it('quotes the text with the author linking back to the post', () => {
    expect(
      quoteBlockData({
        id: 42,
        author: { displayName: 'Ann' },
        content: doc({ type: 'paragraph', data: { text: 'Hello <i>there</i>' } }),
      }),
    ).toEqual({ text: 'Hello there', caption: '<a href="/posts/42">Ann</a>', alignment: 'left' })
  })

  it('escapes names and text so they cannot add markup', () => {
    const data = quoteBlockData({
      id: 1,
      author: { displayName: '<img src=x onerror=y>' },
      content: doc({ type: 'paragraph', data: { text: '1 &lt; 2 and a "quote"' } }),
    })
    expect(data.caption).toBe('<a href="/posts/1">&lt;img src=x onerror=y&gt;</a>')
    expect(data.text).toBe('1 &lt; 2 and a &quot;quote&quot;')
  })
})

describe('escapeHtml', () => {
  it('escapes the characters that matter', () => {
    expect(escapeHtml(`<a href="x">&</a>`)).toBe('&lt;a href=&quot;x&quot;&gt;&amp;&lt;/a&gt;')
  })
})
