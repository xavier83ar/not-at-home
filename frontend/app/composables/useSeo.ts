import type { MaybeRefOrGetter } from 'vue'
import { toValue } from 'vue'

export interface SeoOptions {
  /** Without the site name, which the title template adds. Leave out for the home page. */
  title?: string
  description?: string
  /** The page's own path, without the query (`/topics/1-x`). */
  path: string
  /** For lists: page 2 and up get their own canonical URL. */
  page?: number
  /** Path or URL of the image for link previews; the brand icon when missing. */
  image?: string | null
  type?: 'website' | 'article'
  /** Home › ... › this page, after the implicit "Home"; adds structured data. */
  breadcrumbs?: Breadcrumb[]
}

/**
 * Titles, descriptions, canonical URL, Open Graph and breadcrumb data for an indexable page.
 * Pages that must stay out of search results just set `robots: 'noindex'` instead.
 * The public address comes from `NUXT_PUBLIC_SITE_URL` (read at runtime).
 */
export function useSeo(source: MaybeRefOrGetter<SeoOptions>) {
  const siteUrl = useRuntimeConfig().public.siteUrl
  const options = computed(() => toValue(source))
  const url = computed(() =>
    absoluteUrl(siteUrl, canonicalPath(options.value.path, options.value.page)),
  )
  const image = computed(() => options.value.image || DEFAULT_IMAGE)

  useSeoMeta({
    title: () => options.value.title,
    description: () => options.value.description ?? DEFAULT_DESCRIPTION,
    ogSiteName: SITE_NAME,
    ogType: () => options.value.type ?? 'website',
    ogTitle: () => options.value.title ?? SITE_NAME,
    ogDescription: () => options.value.description ?? DEFAULT_DESCRIPTION,
    ogUrl: () => url.value,
    ogImage: () => absoluteUrl(siteUrl, image.value),
    twitterCard: () => (options.value.image ? 'summary_large_image' : 'summary'),
  })

  useHead({
    link: [{ rel: 'canonical', href: () => url.value }],
    script: () => {
      const crumbs = options.value.breadcrumbs
      return crumbs
        ? [
            {
              type: 'application/ld+json',
              innerHTML: breadcrumbJsonLd(siteUrl, [{ name: 'Home', path: '/' }, ...crumbs]),
            },
          ]
        : []
    },
  })
}
