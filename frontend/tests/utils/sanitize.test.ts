import { describe, expect, it } from 'vitest'
import { inlineText, sanitizeInline } from '~/utils/sanitize'

describe('sanitizeInline', () => {
  it('keeps the formatting Editor.js produces', () => {
    const html = '<b>bold</b> <i>italic</i> <u>under</u> <code>code</code> <mark>marked</mark>'
    expect(sanitizeInline(html)).toBe(html)
  })

  it('drops scripts, handlers, styles and unknown tags', () => {
    expect(
      sanitizeInline(
        '<script>alert(1)</script><b onclick="x()" style="color:red">hi</b><img src=x onerror=y>',
      ),
    ).toBe('<b>hi</b>')
  })

  it('drops links that are not http, https or mailto', () => {
    for (const href of [
      'javascript:alert(1)',
      ' JaVaScRiPt:alert(1)',
      'data:text/html,<script>1</script>',
      '//evil.example/x',
      'vbscript:x',
    ]) {
      expect(sanitizeInline(`<a href="${href}">x</a>`), href).not.toContain('href')
    }
  })

  it('opens links to other sites in a new tab and tells search engines not to follow them', () => {
    const html = sanitizeInline('<a href="https://example.com/a">x</a>')
    expect(html).toContain('href="https://example.com/a"')
    expect(html).toContain('rel="nofollow noopener ugc"')
    expect(html).toContain('target="_blank"')
  })

  it('leaves links within the site alone, and ignores the target and rel it was given', () => {
    const html = sanitizeInline('<a href="/posts/12" target="_blank" rel="opener">quote</a>')
    expect(html).toBe('<a href="/posts/12">quote</a>')
  })

  it('does not let a visitor choose rel or target on an external link', () => {
    const html = sanitizeInline('<a href="https://example.com" rel="opener" target="_self">x</a>')
    expect(html).toContain('rel="nofollow noopener ugc"')
    expect(html).toContain('target="_blank"')
  })

  it('escapes text that looks like markup', () => {
    expect(sanitizeInline('1 &lt; 2 &amp; 3')).toBe('1 &lt; 2 &amp; 3')
  })
})

describe('inlineText', () => {
  it('removes tags and collapses spaces', () => {
    expect(inlineText('A <b>very</b>   nice\nview')).toBe('A very nice view')
  })

  it('decodes entities, including the numeric ones the backend writes', () => {
    expect(inlineText('1 &lt; 2 &amp;&nbsp;a&#43;b&#61;c &#x41; &unknown;')).toBe(
      '1 < 2 & a+b=c A &unknown;',
    )
  })

  it('decodes an escaped ampersand only once', () => {
    expect(inlineText('&amp;lt;')).toBe('&lt;')
  })
})
