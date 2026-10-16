import { describe, expect, it } from 'vitest'
import { absoluteUrl, breadcrumbJsonLd, canonicalPath, excerpt, truncate } from '~/utils/seo'

describe('absoluteUrl', () => {
  it('joins the site and the path with exactly one slash', () => {
    expect(absoluteUrl('https://notathome.example', '/topics/1-x')).toBe(
      'https://notathome.example/topics/1-x',
    )
    expect(absoluteUrl('https://notathome.example/', '/')).toBe('https://notathome.example/')
    expect(absoluteUrl('https://notathome.example', 'icon.png')).toBe(
      'https://notathome.example/icon.png',
    )
  })
})

describe('canonicalPath', () => {
  it('is the plain path for the first page and keeps ?page= after that', () => {
    expect(canonicalPath('/categories/hiking')).toBe('/categories/hiking')
    expect(canonicalPath('/categories/hiking', 1)).toBe('/categories/hiking')
    expect(canonicalPath('/categories/hiking', 3)).toBe('/categories/hiking?page=3')
  })
})

describe('truncate', () => {
  it('leaves short text alone', () => {
    expect(truncate('short', 20)).toBe('short')
  })

  it('cuts at a word and adds an ellipsis', () => {
    expect(truncate('the quick brown fox jumps over the lazy dog', 20)).toBe('the quick brown…')
  })

  it('cuts mid-word when there is no good space', () => {
    expect(truncate('a'.repeat(50), 10)).toBe(`${'a'.repeat(9)}…`)
  })
})

describe('excerpt', () => {
  const doc = (...blocks: { type: string; data: Record<string, unknown> }[]) => ({
    blocks,
  })

  it('takes plain text from the first text blocks, without tags or entities', () => {
    const text = excerpt(
      doc(
        { type: 'header', data: { text: 'Patagonia &amp; beyond', level: 2 } },
        { type: 'paragraph', data: { text: 'Walk <b>far</b>, <a href="/x">often</a>.' } },
      ),
    )
    expect(text).toBe('Patagonia & beyond Walk far, often.')
  })

  it('skips code and images, and reads lists in both formats', () => {
    const text = excerpt(
      doc(
        { type: 'code', data: { code: 'rm -rf /' } },
        { type: 'image', data: { file: { url: '/api/uploads/a.png' } } },
        { type: 'list', data: { style: 'unordered', items: ['boots', { content: 'poles' }] } },
      ),
    )
    expect(text).toBe('boots, poles')
  })

  it('is limited to the given length', () => {
    const long = 'word '.repeat(100)
    const text = excerpt(doc({ type: 'paragraph', data: { text: long } }), 60)
    expect(text.length).toBeLessThanOrEqual(60)
    expect(text.endsWith('…')).toBe(true)
  })

  it('is empty for a missing or textless document', () => {
    expect(excerpt(null)).toBe('')
    expect(excerpt(doc({ type: 'delimiter', data: {} }))).toBe('')
  })
})

describe('breadcrumbJsonLd', () => {
  it('lists the crumbs with absolute URLs and 1-based positions', () => {
    const json = JSON.parse(
      breadcrumbJsonLd('https://notathome.example/', [
        { name: 'Home', path: '/' },
        { name: 'Hiking', path: '/categories/hiking' },
      ]),
    )
    expect(json['@type']).toBe('BreadcrumbList')
    expect(json.itemListElement).toEqual([
      { '@type': 'ListItem', position: 1, name: 'Home', item: 'https://notathome.example/' },
      {
        '@type': 'ListItem',
        position: 2,
        name: 'Hiking',
        item: 'https://notathome.example/categories/hiking',
      },
    ])
  })

  it('cannot be used to close the script tag it is embedded in', () => {
    const raw = breadcrumbJsonLd('https://x.example', [
      { name: '</script><script>alert(1)</script>', path: '/' },
    ])
    expect(raw).not.toContain('<')
    expect(JSON.parse(raw).itemListElement[0].name).toBe('</script><script>alert(1)</script>')
  })
})
